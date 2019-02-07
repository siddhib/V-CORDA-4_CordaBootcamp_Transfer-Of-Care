package com.kotlin_bootcamp.services

import com.kotlin_bootcamp.EHRState
import com.kotlin_bootcamp.ReviewTOCFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("muncipal")
class MuncipalService(val rpcOps: CordaRPCOps) {

    companion object {
        private val logger: Logger = loggerFor<MuncipalService>()
    }

    @GET
    @Path("states")
    @Produces(MediaType.APPLICATION_JSON)
    fun muncipalStates() = rpcOps.vaultQuery(EHRState::class.java).states

    @PUT
    @Path("reviewTOC")
    fun reviewTOC(@QueryParam("ehrID") ehrID: String, @QueryParam("status") status: String): Response {

        return try {

            val signedTx = rpcOps.startFlow(::ReviewTOCFlow,ehrID, status ).returnValue.getOrThrow()
            //  val signedTx = rpcOps.startTrackedFlow(::AdmissionFlow,otherParty,ehrID ).returnValue.getOrThrow()
            // val signedTx = rpcOps.startTrackedFlow(::Initiator, iouValue, otherParty).returnValue.getOrThrow()
            Response.status(Response.Status.CREATED).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }
    }
}