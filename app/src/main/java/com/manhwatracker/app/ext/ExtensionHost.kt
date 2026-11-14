package com.manhwatracker.app.ext

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.api.TypeReference
import java.lang.reflect.Type
import java.security.MessageDigest

/**
 * El "motor" que carga extensiones de Komikku/Mihon (APK ya instalados) y les presta lo que esperan.
 * Basado en ExtensionLoader y MetroInjektRegistrar de Mihon (Apache 2.0).
 */
object ExtensionHost {
    private const val FEATURE = "tachiyomi.extension"
    private const val META_CLASS = "tachiyomi.extension.class"
    private const val META_LIB = "tachiyomix.extensionLib"
    private val SUPPORTED_LIBS = listOf(1.4, 1.5, 1.6)

    @Volatile private var ready = false

    fun init(ctx: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            Injekt = InjektScope(HostRegistrar(ctx.applicationContext as Application))
            ready = true
        }
    }

    class Loaded(val pkg: String, val name: String, val versionName: String, val sources: List<Source>)

    sealed class Result {
        class Ok(val ext: Loaded) : Result()
        /** La extensión es de terceros: hay que pedirle permiso al usuario (una vez por firma). */
        class NeedsTrust(val signatures: List<String>, val name: String) : Result()
        class Fail(val message: String, val detail: String) : Result()
    }

    private val Throwable.root: Throwable get() = generateSequence(this) { it.cause }.last()

    @Suppress("DEPRECATION")
    private val FLAGS = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    fun signatures(p: PackageInfo): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = p.signingInfo ?: return emptyList()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else p.signatures
        return sigs?.map { sha256(it.toByteArray()) }.orEmpty()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("sources", Context.MODE_PRIVATE)
    fun trusted(c: Context): Set<String> = prefs(c).getStringSet("trusted_sigs", emptySet()).orEmpty()
    fun trust(c: Context, sigs: List<String>) = prefs(c).edit().putStringSet("trusted_sigs", trusted(c) + sigs).apply()

    /** Paquetes instalados que son extensiones. */
    fun installedExtensions(c: Context): List<PackageInfo> = try {
        val pm = c.packageManager
        val all = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(FLAGS.toLong())) else pm.getInstalledPackages(FLAGS)
        all.filter { p -> p.reqFeatures.orEmpty().any { it.name == FEATURE } }
    } catch (e: Throwable) { emptyList() }

    fun load(c: Context, pkg: String): Result {
        init(c)
        val pm = c.packageManager
        try {
            val info = try { pm.getPackageInfo(pkg, FLAGS) } catch (e: PackageManager.NameNotFoundException) {
                return Result.Fail("La extensión no está instalada.", e.stackTraceToString())
            }
            if (info.reqFeatures.orEmpty().none { it.name == FEATURE }) return Result.Fail("Ese paquete no es una extensión.", "")
            val app = info.applicationInfo ?: return Result.Fail("Extensión mal formada (sin información de la app).", "")
            val meta = app.metaData ?: return Result.Fail("Extensión mal formada (sin metadatos).", "")
            val name = meta.getString("tachiyomix.name") ?: pm.getApplicationLabel(app).toString().substringAfter("Tachiyomi: ")
            val versionName = info.versionName.orEmpty()
            val lib = meta.getFloat(META_LIB).takeUnless { it == 0.0f }?.toString()?.toDouble()
                ?: versionName.substringBeforeLast('.').toDoubleOrNull()
            if (lib == null || lib !in SUPPORTED_LIBS) return Result.Fail("Versión de extensión no compatible ($lib). Se soportan: ${SUPPORTED_LIBS.joinToString()}.", "")
            val sigs = signatures(info)
            if (sigs.isEmpty()) return Result.Fail("La extensión no está firmada.", "")
            if (sigs.none { it in trusted(c) }) return Result.NeedsTrust(sigs, name)
            val classes = meta.getString(META_CLASS)
            if (classes.isNullOrBlank()) return Result.Fail("Extensión mal formada (sin clase de fuente).", "")
            val loader = DelegateLastPathClassLoader(app.sourceDir, null, c.classLoader)
            val sources = ArrayList<Source>()
            for (raw in classes.split(";")) {
                val cn = raw.trim().let { if (it.startsWith(".")) pkg + it else it }
                val obj = Class.forName(cn, false, loader).getDeclaredConstructor().newInstance()
                when (obj) {
                    is Source -> sources.add(obj)
                    is SourceFactory -> sources.addAll(obj.createSources())
                    else -> return Result.Fail("Tipo de fuente desconocido: ${obj.javaClass}", "")
                }
            }
            return Result.Ok(Loaded(pkg, name, versionName, sources))
        } catch (e: Throwable) {
            return Result.Fail(e.root.let { "${it.javaClass.simpleName}: ${it.message}" }, e.stackTraceToString())
        }
    }

    /** Qué ofrece la app a las extensiones por Injekt (igual que Mihon: solo lectura). */
    private class HostRegistrar(private val app: Application) : InjektRegistrar {
        private val json by lazy { Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true } }
        private val protoBuf by lazy { ProtoBuf }
        private val network by lazy { NetworkHelper(app) }
        private val js by lazy { JavaScriptEngine(app) }

        private val bindings = mapOf<Type, () -> Any>(
            Application::class.java to { app },
            Context::class.java to { app },
            Json::class.java to { json },
            ProtoBuf::class.java to { protoBuf },
            NetworkHelper::class.java to { network },
            JavaScriptEngine::class.java to { js },
        )

        override fun <R : Any> getInstance(forType: Type): R = getInstanceOrNull(forType)
            ?: throw InjektionException("$forType no está disponible para las extensiones")
        override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R = getInstanceOrNull(forType) ?: default
        override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R = getInstanceOrNull(forType) ?: default()
        @Suppress("UNCHECKED_CAST")
        override fun <R : Any> getInstanceOrNull(forType: Type): R? = bindings[forType]?.invoke() as R?
        override fun <R : Any, K : Any> getKeyedInstance(forType: Type, key: K): R = unsupported()
        override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: R): R = unsupported()
        override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: () -> R): R = unsupported()
        @Suppress("RedundantNullableReturnType")
        override fun <R : Any, K : Any> getKeyedInstanceOrNull(forType: Type, key: K): R? = unsupported()
        override fun <R : Any> getLogger(expectedLoggerType: Type, byName: String): R = unsupported()
        override fun <R : Any, T : Any> getLogger(expectedLoggerType: Type, forClass: Class<T>): R = unsupported()
        override fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T) = readOnly()
        override fun <R : Any> addSingletonFactory(forType: TypeReference<R>, factoryCalledOnce: () -> R) = readOnly()
        override fun <R : Any> addFactory(forType: TypeReference<R>, factoryCalledEveryTime: () -> R) = readOnly()
        override fun <R : Any> addPerThreadFactory(forType: TypeReference<R>, factoryCalledOncePerThread: () -> R) = readOnly()
        override fun <R : Any, K : Any> addPerKeyFactory(forType: TypeReference<R>, factoryCalledPerKey: (K) -> R) = readOnly()
        override fun <R : Any, K : Any> addPerThreadPerKeyFactory(forType: TypeReference<R>, factoryCalledPerKeyPerThread: (K) -> R) = readOnly()
        override fun <R : Any> addLoggerFactory(forLoggerType: TypeReference<R>, factoryByName: (String) -> R, factoryByClass: (Class<Any>) -> R) = readOnly()
        override fun <O : Any, T : O> addAlias(existingRegisteredType: TypeReference<T>, otherAncestorOrInterface: TypeReference<O>) = readOnly()
        override fun <T : Any> hasFactory(forType: TypeReference<T>): Boolean = forType.type in bindings
        override fun importModule(submodule: InjektModule) = readOnly()
        private fun readOnly(): Nothing = throw UnsupportedOperationException("Solo lectura")
        private fun unsupported(): Nothing = throw UnsupportedOperationException("No soportado")
    }
}
