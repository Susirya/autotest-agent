package com.epam.drill.auto.test.agent.config

import com.epam.drill.auto.test.agent.*
import com.epam.drill.jvmapi.*
import com.epam.drill.jvmapi.gen.*
import com.epam.drill.logger.*
import kotlinx.cinterop.*
import kotlinx.serialization.Properties
import kotlinx.serialization.Serializable
import kotlin.native.concurrent.*

@Serializable
data class AgentConfig(
    val agentId: String = "",
    val groupId: String = "",
    val pluginId: String = "",
    val adminHost: String = "",
    val adminPort: String = "80",
    val runtimePath: String = "",
    val logLevel: String = LogLevel.ERROR.name,
    val rawFrameworkPlugins: String = ""
){
    val level: LogLevel
        get() = LogLevel.valueOf(logLevel)

    val frameworkPlugins: List<String>
        get() = rawFrameworkPlugins.split(";")
}

const val WRONG_PARAMS = "Agent parameters are not specified correctly."

fun String?.toAgentParams() = this.asParams().let { params ->
    val result = Properties.load<AgentConfig>(params)
    if (result.agentId.isBlank() && result.groupId.isBlank()) {
        error(WRONG_PARAMS)
    }
    logConfig.value = configByLoggerLevel(result.level).freeze()
    result
}

fun String?.asParams(): Map<String, String> = try {
    this?.split(",")?.filter { it.isNotEmpty() }?.associate {
        val (key, value) = it.split("=")
        key to value
    } ?: emptyMap()
} catch (parseException: Exception) {
    throw IllegalArgumentException(WRONG_PARAMS)
}

fun CPointer<JavaVMVar>.initAgent(runtimePath: String) = memScoped {
    initAgentGlobals()
    setUnhandledExceptionHook({ thr: Throwable ->
        mainLogger.error { "Unhandled event $thr" }
    }.freeze())
    val jvmtiCapabilities = alloc<jvmtiCapabilities>()
    jvmtiCapabilities.can_retransform_classes = 1.toUInt()
    jvmtiCapabilities.can_retransform_any_class = 1.toUInt()
    jvmtiCapabilities.can_maintain_original_method_order = 1.toUInt()
    AddCapabilities(jvmtiCapabilities.ptr)
    AddToBootstrapClassLoaderSearch("$runtimePath/drillRuntime.jar")
    callbackRegister()
}

fun CPointer<JavaVMVar>.initAgentGlobals() {
    vmGlobal.value = freeze()
    setJvmti(pointed)
}

private fun setJvmti(vm: JavaVMVar) = memScoped {
    val jvmtiEnvPtr = alloc<CPointerVar<jvmtiEnvVar>>()
    vm.value!!.pointed.GetEnv!!(vm.ptr, jvmtiEnvPtr.ptr.reinterpret(), JVMTI_VERSION.convert())
    jvmti.value = jvmtiEnvPtr.value
    jvmtiEnvPtr.value.freeze()
}