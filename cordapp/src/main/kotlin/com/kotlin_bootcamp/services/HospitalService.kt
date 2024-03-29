package com.kotlin_bootcamp.services

import com.kotlin_bootcamp.*

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow

import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
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
    fun states() = rpcOps.vaultQuery(EHRState::class.java).states

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
    @Path("admit")
    fun admitPatient(@QueryParam("ehrID") ehrID: String, @QueryParam("partyName") partyName: String ): Response {

        val otherParty = rpcOps.partiesFromName(partyName, true).firstOrNull()?:return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()


        return try {
            val signedTx = rpcOps.startFlow(::AdmissionFlow,otherParty,ehrID ).returnValue.getOrThrow()


           Response.status(CREATED).entity("Patient with ehr  $ehrID admitted to hospital.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    @PUT
    @Path("discharge")
    fun dischargePatient(@QueryParam("ehrID") ehrID: String, @QueryParam("partyName") partyName: String , @QueryParam("dischargeDocument") dischargeDocument: String ): Response {

        val otherParty = rpcOps.partiesFromName(partyName, true).firstOrNull()?:return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()

        if (dischargeDocument == null) {
            return Response.status(BAD_REQUEST).entity(" Discharge document is missing.\n").build()
        }

        var dischargeFile = File(dischargeDocument)
        if (!dischargeFile.exists()) {
            return Response.status(BAD_REQUEST).entity(" Specified discharge document $dischargeDocument does not exist.\n").build()
        }
        return try {

            logger.info("Uploading the discharge document")
            val inputStream: InputStream = dischargeFile.inputStream()

            val txHash = rpcOps.uploadAttachment(inputStream)

            logger.info("Discharge document uploaded with hash ${txHash}")


            val signedTx = rpcOps.startFlow(::DischargeFlow,otherParty,ehrID, txHash ).returnValue.getOrThrow()

            Response.status(CREATED).entity("Discharge patient successful , transaction id ${signedTx.id}\n").build()


        } catch (ex: Throwable) {
            logger.error("Error discharging patient ")
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }


    @PUT
    @Path("addEvent")
    fun addEvent(@QueryParam("ehrID") ehrID: String, @QueryParam("partyName") partyName: String,
                 @QueryParam("medicalEvent") medicalEvent: String ): Response {

        val otherParty = rpcOps.partiesFromName(partyName, true).firstOrNull()?:return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()
        if (medicalEvent == null) {
            return Response.status(BAD_REQUEST).entity(" Please add medical event\n").build()
        }

        return try {
            val signedTx = rpcOps.startFlow(::UpdateEHRFlow,otherParty,ehrID , medicalEvent).returnValue.getOrThrow()

            Response.status(CREATED).entity("Event successfully added. \n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    @PUT
    @Path("initiateTOC")
    fun initiateTOC(@QueryParam("ehrID") ehrID: String, @QueryParam("partyName") partyName: String,
                 @QueryParam("toHospital") toHospital: String ): Response {

        val otherParty = rpcOps.partiesFromName(partyName, true).firstOrNull()?:return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()

        val otherHospitalParty = rpcOps.partiesFromName(toHospital, true).firstOrNull()?:return Response.status(BAD_REQUEST).entity("Party named $toHospital cannot be found.\n").build()


        return try {
            val signedTx = rpcOps.startFlow(::RequestTOCFlow,otherParty, otherHospitalParty, ehrID ).returnValue.getOrThrow()

            Response.status(CREATED).entity("Transfer of care requested for patient with ehrId $ehrID.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

}