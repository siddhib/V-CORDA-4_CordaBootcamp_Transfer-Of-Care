package com.kotlin_bootcamp.com.kotlin_bootcamp.plugin

import com.kotlin_bootcamp.services.HospitalService
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class TOCPlugin: WebServerPluginRegistry {
    override val webApis = listOf(Function(::HospitalService))

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     */
//    override val staticServeDirs = mapOf(
//            // This will serve the exampleWeb directory in resources to /web/example
//            "toc" to javaClass.classLoader.getResource("tocWeb").toExternalForm()
//    )
}