package com.kotlin_bootcamp

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class EHRContract: Contract {
    companion object {
        val EHR_contract_id = "com.kotlin_bootcamp.EHRContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value){
            is Commands.Admission -> verifyAdmission(tx, command)
            is Commands.UpdateEHR -> verifyUpdateEHR(tx, command)
            is Commands.Discharge -> verifyDischarge(tx, command)
            is Commands.RequestTransferOfCare -> verifyRequestTransferOfCare(tx, command)
            is Commands.ReviewTransferOfCare -> verifyReviewTransferOfCare(tx, command)
        }
    }

    private fun verifyReviewTransferOfCare(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            "Transaction should have one input" using (tx.inputs.size == 1)
            "Transaction should have one output" using (tx.outputs.size == 1)
            val inputState = tx.inputStates.get(0) as EHRState
            val outputState = tx.outputStates.get(0) as EHRState
            "Request should be signed by the new hospital and municipal council" using (command.signers.containsAll(listOf(outputState.hospital.owningKey,outputState.municipalCouncil.owningKey)))
            "Command should be of type ReviewTransferOfCare" using (command.value is Commands.ReviewTransferOfCare)
            "CareStatus should be of type Transfer of Care Requested" using (inputState.careStatus == "TRANSFER OF CARE REQUESTED")
        }
    }

    private fun verifyDischarge(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            "Transaction should have one input" using (tx.inputs.size == 1)
            "Transaction should have one output" using (tx.outputs.size == 1)
            val outputState = tx.outputStates.get(0) as EHRState
            "Request should be signed by the hospital and municipal council" using (command.signers.containsAll(listOf(outputState.hospital.owningKey,outputState.municipalCouncil.owningKey)))
            "Command should be of type Discharge" using (command.value is Commands.Discharge)
            "Discharge documents should be attached" using (tx.attachments.size == 2)
            val inputState = tx.inputStates.get(0) as EHRState
            "Patient needs to be admitted before discharging" using (inputState.careStatus == "ADMITTED")
        }

    }

    private fun verifyUpdateEHR(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            "Transaction should have one input" using (tx.inputs.size == 1)
            "Transaction should have one output" using (tx.outputs.size == 1)
            val outputState = tx.outputStates.get(0) as EHRState
            "Request should be signed by the hospital and municipal council" using (command.signers.containsAll(listOf(outputState.hospital.owningKey,outputState.municipalCouncil.owningKey)))
            "Command should be of type UpdateEHR" using (command.value is Commands.UpdateEHR)
            val inputState = tx.inputStates.get(0) as EHRState
            "Patient needs to be admitted before adding medical event details" using (inputState.careStatus == "ADMITTED")
        }
    }

    private fun verifyRequestTransferOfCare(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            "Transaction should have one input" using (tx.inputs.size == 1)
            "Transaction should have one output" using (tx.outputs.size == 1)
            val inputState = tx.inputStates.get(0) as EHRState
            val outputState = tx.outputStates.get(0) as EHRState
            "Request should be signed by both the hospital" using command.signers.containsAll(listOf(inputState.hospital.owningKey,outputState.hospital.owningKey))
            "Command should be of type RequestTransferOfCare" using (command.value is Commands.RequestTransferOfCare)
            "Patient is not admitted" using (inputState.careStatus == "ADMITTED" || inputState.careStatus == "TRANSFER OF CARE REJECTED")
        }
    }

    private fun verifyAdmission(tx: LedgerTransaction, command: CommandWithParties<Commands>) {
        requireThat {
            "Transaction should have zero or one input" using (tx.inputStates.size <= 1)
            "Transaction should have one output" using (tx.outputs.size == 1)
            val outputState = tx.outputStates.get(0) as EHRState
            "Request should be signed by the hospital and municipal council" using (command.signers.containsAll(listOf(outputState.hospital.owningKey,outputState.municipalCouncil.owningKey)))
            "Command should be of type Admission" using (command.value is Commands.Admission)
            if(tx.inputStates.isNotEmpty()){
                val inputState = tx.inputStates.get(0) as EHRState
                "Patient cannot be admitted" using (inputState.careStatus == "DISCHARGED" || inputState.careStatus == "TRANSFER OF CARE APPROVED")
            }
        }
    }

    interface Commands : CommandData{
        class Admission : Commands
        class UpdateEHR : Commands
        class Discharge : Commands
        class RequestTransferOfCare : Commands
        class ReviewTransferOfCare : Commands
    }

}