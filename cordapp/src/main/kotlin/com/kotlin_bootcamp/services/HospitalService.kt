package com.kotlin_bootcamp.services

import com.kotlin_bootcamp.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
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
    fun hospitalStates() = rpcOps.vaultQuery(EHRState::class.java).states

    @GET
    @Path("state/{ehrId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun hospitalStateByEhrId(@PathParam("ehrId") ehrID: String) {

//        val logicalExpression = builder { EHRSchema.::currency.equal(GBP.currencyCode) }
//        val ehr = EHRSchema::ehrId.equal(ehrID)
//
//
//        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(ehr)
//
//        rpcOps.vaultQueryByCriteria(EHRState::class.java)
        rpcOps.vaultQuery(EHRState::class.java).states
    }


    @PUT
    @Path("admit")
    fun admitPatient(@QueryParam("ehrID") ehrID: String, @QueryParam("partyName") partyName: String ): Response {

        val otherParty = rpcOps.partiesFromName(partyName, true).firstOrNull()?:return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()


        return try {
            val signedTx = rpcOps.startFlow(::AdmissionFlow,otherParty,ehrID ).returnValue.getOrThrow()
          //  val signedTx = rpcOps.startTrackedFlow(::AdmissionFlow,otherParty,ehrID ).returnValue.getOrThrow()
           // val signedTx = rpcOps.startTrackedFlow(::Initiator, iouValue, otherParty).returnValue.getOrThrow()
           Response.status(CREATED).entity("Patient with ehr  $ehrID admitted to hospital.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    @PUT
    @Path("discharge")
    fun dischargePatient(@QueryParam("ehrID") ehrID: String, @QueryParam("partyName") partyName: CordaX500Name , @QueryParam("dischargeDocument") dischargeDocument: String ): Response {

        val otherParty = rpcOps.wellKnownPartyFromX500Name(partyName) ?:
        return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()

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

            //rpcOps.startFlow(::DischargeFlow, otherParty,ehrID, txHash).returnValue.getOrThrow()
            val signedTx = rpcOps.startFlow(::DischargeFlow,otherParty,ehrID, txHash ).returnValue.getOrThrow()

            Response.status(CREATED).entity("Discharge Transaction successful , transaction id ${signedTx.id} committed to ledger.\n").build()

            Response.status(CREATED).build()
        } catch (ex: Throwable) {
            logger.error("Error discharging patient ")
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }


    @PUT
    @Path("addEvent")
    fun addEvent(@QueryParam("ehrID") ehrID: String, @QueryParam("partyName") partyName: CordaX500Name,
                 @QueryParam("medicalEvent") medicalEvent: String ): Response {

        val otherParty = rpcOps.wellKnownPartyFromX500Name(partyName) ?:
        return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()
        if (medicalEvent == null) {
            return Response.status(BAD_REQUEST).entity(" Please add medical event\n").build()
        }

        return try {
            val signedTx = rpcOps.startFlow(::UpdateEHRFlow,otherParty,ehrID , medicalEvent).returnValue.getOrThrow()
            //  val signedTx = rpcOps.startTrackedFlow(::AdmissionFlow,otherParty,ehrID ).returnValue.getOrThrow()
            // val signedTx = rpcOps.startTrackedFlow(::Initiator, iouValue, otherParty).returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()

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
            //  val signedTx = rpcOps.startTrackedFlow(::AdmissionFlow,otherParty,ehrID ).returnValue.getOrThrow()
            // val signedTx = rpcOps.startTrackedFlow(::Initiator, iouValue, otherParty).returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

}