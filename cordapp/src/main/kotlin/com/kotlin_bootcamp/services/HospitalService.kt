package com.kotlin_bootcamp.services

import com.kotlin_bootcamp.AdmissionFlow
import com.kotlin_bootcamp.EHRState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import javax.ws.rs.*


import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED

import javax.ws.rs.core.MediaType
import org.slf4j.Logger
import java.io.File
import java.io.InputStream

@Path("hospital")
class HospitalService(val rpcOps: CordaRPCOps) {

    companion object {
        private val logger: Logger = loggerFor<HospitalService>()
    }

    @GET
    @Path("states")
    @Produces(MediaType.APPLICATION_JSON)
    fun hospitalStates() = rpcOps.vaultQuery(EHRState::class.java).states


    @PUT
    @Path("admit")
    fun admitPatient(@QueryParam("ehrID") ehrID: String, @QueryParam("partyName") partyName: CordaX500Name ): Response {

        val otherParty = rpcOps.wellKnownPartyFromX500Name(partyName) ?:
        return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()

        return try {

            val signedTx = rpcOps.startTrackedFlow(::AdmissionFlow,otherParty,ehrID ).returnValue.getOrThrow()
           // val signedTx = rpcOps.startTrackedFlow(::Initiator, iouValue, otherParty).returnValue.getOrThrow()
           Response.status(CREATED).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    @PUT
    @Path("upload")
    fun uploadAttachment( ): Response {



        return try {

            val inputStream: InputStream = File("C:\\discharge1.zip").inputStream()
            val tx = rpcOps.uploadAttachment(inputStream);

            Response.status(CREATED).entity("Transaction hash ${tx} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    /**
     * Returns the node's name.
     */
//    @GET
//    @Path("me")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun whoami() = mapOf("me" to myLegalName)

}