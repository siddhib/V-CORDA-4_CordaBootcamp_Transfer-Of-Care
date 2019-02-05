package com.kotlin_bootcamp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class ReviewTOCFlow(val ehrID: String,
                    val status: String): FlowLogic<SignedTransaction>() {
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

        val inputState = vaultQueryCriteria.states.first()
        val oldHospital = inputState.state.data.hospital
        val newHospital = inputState.state.data.transferToHospital!!
        val outputState: EHRState
        //Update medical event
        var medEvents=inputState.state.data.medicalEvents

        if(status == "Approve"){

            medEvents+="::Approved Transfer of Care to "+newHospital.name.organisation
            outputState = inputState.state.data.copy(hospital = newHospital, transferToHospital = null, medicalEvents = medEvents, careStatus = "TRANSFER OF CARE APPROVED", participants = listOf(newHospital,ourIdentity))
        }
        else if(status == "Reject"){
            medEvents+="::Rejected Transfer of Care to "+newHospital.name.organisation
            outputState = inputState.state.data.copy(careStatus = "TRANSFER OF CARE REJECTED",medicalEvents = medEvents, participants = listOf(oldHospital,ourIdentity))
        }
        else{
            throw FlowException("Status should be Approve/Reject")
        }

        //Build the transaction
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(EHRContract.Commands.ReviewTransferOfCare(), ourIdentity.owningKey, outputState.hospital.owningKey)
                .addInputState(inputState)
                .addOutputState(outputState, EHRContract.EHR_contract_id)
        println("Done building transaction")

        // Verify the transaction builder
        transactionBuilder.verify(serviceHub)

        println("partial sign transaction")
        // Sign the transaction
        val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        println("Initiate flow")
        // Send transaction to the seller node for signing
        val otherPartySession = initiateFlow(outputState.hospital)

        println("Collect signs")
        val completelySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(otherPartySession)))

        // Notarize and commit
        return subFlow(FinalityFlow(completelySignedTransaction))
    }

}

@InitiatedBy(ReviewTOCFlow::class)
class ReviewTOCResponderFlow(val otherpartySession: FlowSession): FlowLogic<Unit>(){
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