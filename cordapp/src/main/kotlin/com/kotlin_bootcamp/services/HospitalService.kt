package com.kotlin_bootcamp.services

import com.kotlin_bootcamp.EHRState
import net.corda.core.messaging.CordaRPCOps

import javax.ws.rs.Produces
import javax.ws.rs.Path
import javax.ws.rs.GET

import javax.ws.rs.core.MediaType

@Path("hospital")
class HospitalService(val rpcOps: CordaRPCOps) {

    @GET
    @Path("states")
    @Produces(MediaType.APPLICATION_JSON)
    fun hospitalStates() = rpcOps.vaultQuery(EHRState::class.java).states

    /**
     * Returns the node's name.
     */
//    @GET
//    @Path("me")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun whoami() = mapOf("me" to myLegalName)

}