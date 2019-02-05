package com.kotlin_bootcamp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class RequestTOCFlow(val municipalCouncil: Party,
                     val toHospital: Party,
                    val ehrID: String): FlowLogic<SignedTransaction>() {
    override val progressTracker: ProgressTracker? = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {

        // Get the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Query vault by ehrID
        println("Querying vault...")
        val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal(ehrID) }
        val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
        val vaultQueryCriteria = serviceHub.vaultService.queryBy<EHRState>(criteria)

        if(vaultQueryCriteria.states.isEmpty()){
            throw FlowException("There is no patient admitted with ehrID:"+ehrID)
        }
        val inputState = vaultQueryCriteria.states.first()

        var medEvents=inputState.state.data.medicalEvents
        medEvents+="::Requested Transfer of Care to "+toHospital.name.organisation

        val outputState = inputState.state.data.copy(transferToHospital = toHospital,medicalEvents = medEvents, careStatus = "TRANSFER OF CARE REQUESTED", participants = listOf(ourIdentity,municipalCouncil))

        // Build the transaction
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(EHRContract.Commands.RequestTransferOfCare(), ourIdentity.owningKey, toHospital.owningKey)
                .addInputState(inputState)
                .addOutputState(outputState, EHRContract.EHR_contract_id)
        println("Done building transaction")

        // Verify the transaction builder
        transactionBuilder.verify(serviceHub)

        println("Partially sign transaction")
        // Sign the transaction
        val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        println("Initiate flow")
        // Send transaction to the seller node for signing
        val otherPartySession = initiateFlow(toHospital)

        println("Collect signs")
        val completelySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(otherPartySession)))


        // Notarize and commit
        println("Notarize and commit")
        return subFlow(FinalityFlow(completelySignedTransaction))
    }
}

@InitiatedBy(RequestTOCFlow::class)
class RequestTOCResponderFlow(val otherpartySession: FlowSession): FlowLogic<Unit>(){
    @Suspendable
    override fun call() {

        //Sign the transaction
        println("Signing the transaction")
        val flow = object : SignTransactionFlow(otherpartySession){
            override fun checkTransaction(stx: SignedTransaction) {
                // sanity checks on this transaction
            }
        }
        subFlow(flow)
    }
}