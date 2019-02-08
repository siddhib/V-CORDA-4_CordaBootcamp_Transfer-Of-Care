package com.kotlin_bootcamp.services

import com.kotlin_bootcamp.EHRSchemaV1
import com.kotlin_bootcamp.EHRState
import com.kotlin_bootcamp.ReviewTOCFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
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

    @GET
    @Path("state/byEhrId/{ehrID}")
    @Produces(MediaType.APPLICATION_JSON)
    fun stateByEhrId(@PathParam("ehrID") ehrID: String) : Response{

        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
        val results = builder {
            var ehrCriteria = EHRSchemaV1.PersistentEHR::ehrId.equal(ehrID)
            val customCriteria = QueryCriteria.VaultCustomQueryCriteria(ehrCriteria)
            val criteria = generalCriteria.and(customCriteria)
            val results = rpcOps.vaultQueryBy<EHRState>(criteria).states
            return Response.ok(results).build()
        }

    }



    @PUT
    @Path("reviewTOC")
    @Produces(MediaType.APPLICATION_JSON)
    fun reviewTOC(@QueryParam("ehrID") ehrID: String, @QueryParam("status") status: String): Response {

        return try {

            val signedTx = rpcOps.startFlow(::ReviewTOCFlow,ehrID, status ).returnValue.getOrThrow()

            Response.status(Response.Status.CREATED).entity("Transfer of care status updated.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }
    }
}