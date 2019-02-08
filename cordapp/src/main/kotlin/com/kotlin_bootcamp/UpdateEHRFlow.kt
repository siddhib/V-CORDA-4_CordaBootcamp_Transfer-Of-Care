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
import net.corda.core.utilities.unwrap
import org.intellij.lang.annotations.Flow

@InitiatingFlow
@StartableByRPC
class UpdateEHRFlow(val municipalCouncil: Party,
                    val ehrID: String,
                    val medicalEvent: String): FlowLogic<SignedTransaction>() {
    override val progressTracker: ProgressTracker? = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {

        if(medicalEvent == ""){
            throw FlowException("Medical event details cant be empty")
        }

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

        //Update medical event
        var medEvents=inputState.state.data.medicalEvents
        medEvents+="::"+medicalEvent
        val outputState = inputState.state.data.copy(medicalEvents = medEvents,participants = listOf(ourIdentity,municipalCouncil))

        // Build the transaction
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(EHRContract.Commands.UpdateEHR(), ourIdentity.owningKey, municipalCouncil.owningKey)
                .addInputState(inputState)
                .addOutputState(outputState, EHRContract.EHR_contract_id)
        println("Done building transaction")

        // Verify the transaction builder
        println("Verifying transaction")
        transactionBuilder.verify(serviceHub)


        // Sign the transaction
        println("Partially sign transaction")
        val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        // Send transaction to the municipalCouncil for signing
        println("Collecting signature from municipalCouncil")
        val otherPartySession = initiateFlow(municipalCouncil)
        val completelySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(otherPartySession)))

        // Notarize and commit
        println("Notarize and commit")
        return subFlow(FinalityFlow(completelySignedTransaction))
    }
}

@InitiatedBy(UpdateEHRFlow::class)
class UpdateEHRResponderFlow(val otherpartySession: FlowSession): FlowLogic<Unit>(){
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