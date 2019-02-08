package com.kotlin_bootcamp.com.kotlin_bootcamp.plugin

import com.kotlin_bootcamp.services.HospitalService
import com.kotlin_bootcamp.services.MuncipalService
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class TOCPlugin: WebServerPluginRegistry {
    override val webApis = listOf(Function(::HospitalService), Function(::MuncipalService))


}