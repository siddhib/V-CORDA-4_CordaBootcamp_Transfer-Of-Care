package com.kotlin_bootcamp
import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
@InitiatingFlow
@StartableByRPC
class AdmissionFlow(val municipalCouncil: Party,
                    val ehrID: String): FlowLogic<SignedTransaction>() {

    override val progressTracker: ProgressTracker? = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {
        //Initiate flow with municipalCouncil
        println("Initiate flow")
        val otherPartySession = initiateFlow(municipalCouncil)

        //Send ehrID to municipal
        println("Sending ehrID to municipalCouncil")
        otherPartySession.send(ehrID)

        //Receiving inputs list from municipalCouncil
        println("Receiving inputs list from municipalCouncil")
        val inputStateList=otherPartySession.receive<List<StateAndRef<EHRState>>>().unwrap{ it }

        // Get the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Build the transaction
        println("Building transaction")
        val transactionBuilder = TransactionBuilder(notary)
                .addCommand(EHRContract.Commands.Admission(), ourIdentity.owningKey, municipalCouncil.owningKey)

        val outputState: EHRState
        if(inputStateList.isEmpty()){
            println("InputList is empty")
            outputState = EHRState(municipalCouncil,ourIdentity,null, ehrID, "Admitted to "+ourIdentity.name.organisation, "ADMITTED",participants = listOf(ourIdentity,municipalCouncil))
        }
        else{
            println("InputList is not empty")
            val inputState = inputStateList.get(0)
            if(inputState.state.data.careStatus == "ADMITTED"){
                throw FlowException("Patient is already admitted!!")
            }
            transactionBuilder.addInputState(inputState)

            //Update medical event
            var medEvents=inputState.state.data.medicalEvents
            medEvents+="::Admitted to"+ourIdentity.name.organisation
            outputState = inputState.state.data.copy(careStatus = "ADMITTED", hospital = ourIdentity, medicalEvents = medEvents, participants = listOf(ourIdentity,municipalCouncil))
        }
        transactionBuilder.addOutputState(outputState,EHRContract.EHR_contract_id)
        println("Done building transaction")

        // Verify the transaction builder
        println("Verifying transaction")
        transactionBuilder.verify(serviceHub)

        // Sign the transaction
        println("Partially sign transaction")
        val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        println("Collecting signature from municipalCouncil")
        val completelySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(otherPartySession)))

        // Notarize and commit
        println("Notarize and commit")
        return subFlow(FinalityFlow(completelySignedTransaction))
    }
}
@InitiatedBy(AdmissionFlow::class)
class AdmissionResponderFlow(val otherpartySession: FlowSession): FlowLogic<Unit>(){

    @Suspendable
    override fun call() {

        //Receiving ehrID from hospital
        println("Receiving ehrID from hospital")
        val ehrID: String = otherpartySession.receive<String>().unwrap(){ it }

        // Query vault by ehrID
        println("Querying vault...")
        val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal(ehrID) }
        val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
        val vaultQueryCriteria = serviceHub.vaultService.queryBy<EHRState>(criteria)

        val inputState: List<StateAndRef<EHRState>>
        if(vaultQueryCriteria.states.isEmpty()){
            println("EHR state does not exist")
            inputState = emptyList()
        }
        else {
            println("Found EHR state")
            inputState = listOf(vaultQueryCriteria.states.first())
        }

        val flow = object : SignTransactionFlow(otherpartySession){
            override fun checkTransaction(stx: SignedTransaction) {
                // sanity checks on this transaction
            }
        }

        //Send input states to hospital
        println("Sending input states to hospital")
        otherpartySession.send(inputState)

        //Sign the transaction
        println("Signing the transaction")
        subFlow(flow)
    }
}