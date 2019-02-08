package kotlin_bootcamp

import com.kotlin_bootcamp.EHRContract
import com.kotlin_bootcamp.EHRState
import net.corda.core.contracts.Contract
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.InputStreamAndHash
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class EHRContractTests {
    val hospitalA = TestIdentity(CordaX500Name("Hospital A", "", "GB")).party
    val hospitalB = TestIdentity(CordaX500Name("Hospital B", "", "GB")).party
    val municipalCouncil = TestIdentity(CordaX500Name("Municipal Council", "", "GB")).party
    private val ledgerServices = MockServices(TestIdentity(CordaX500Name("TestId", "", "GB")))

    @Test
    fun ehrContractImplementsContract() {
        assert((EHRContract() is Contract))
    }
    @Test
    fun admissionCommandVerification() {

        val inputState1 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","ADMITTED", listOf(hospitalA,municipalCouncil))
        val inputState2 = EHRState(municipalCouncil,hospitalB,null,"testid","testevent1","ADMITTED", listOf(hospitalB,municipalCouncil))
        val inputState3 = EHRState(municipalCouncil,hospitalB,null,"testid","testevent1","TRANSFER OF CARE APPROVED", listOf(hospitalB,municipalCouncil))
        val outputState1 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent","ADMITTED", listOf(hospitalA,municipalCouncil))
        val outputState2 = EHRState(municipalCouncil,hospitalB,null,"testid","testevent","ADMITTED", listOf(hospitalB,municipalCouncil))

        ledgerServices.ledger {
            transaction {
                attachment(contractClassName = EHRContract.EHR_contract_id)

                tweak {
                    // more than 1 input
                    input(EHRContract.EHR_contract_id, inputState1)
                    input(EHRContract.EHR_contract_id, inputState2)
                    command(hospitalA.owningKey, EHRContract.Commands.Admission())
                    failsWith("Transaction should have zero or one input")
                }
                tweak {
                    // no output
                    input(EHRContract.EHR_contract_id, inputState1)
                    command(hospitalA.owningKey, EHRContract.Commands.Admission())
                    failsWith("Transaction should have one output")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    //add output
                    output(EHRContract.EHR_contract_id, outputState1)
                    // wrong Signers
                    command(hospitalA.owningKey, EHRContract.Commands.Admission())
                    failsWith("Request should be signed by the hospital and municipal council")
                }
                tweak {
                    // wrong careStatus in input
                    input(EHRContract.EHR_contract_id, inputState2)
                    //add output
                    output(EHRContract.EHR_contract_id, outputState1)
                    command(listOf(hospitalA.owningKey, municipalCouncil.owningKey), EHRContract.Commands.Admission())
                    failsWith("Patient cannot be admitted")
                }
                tweak {
                    // TOC not requested by this hospital
                    input(EHRContract.EHR_contract_id, inputState3)
                    //add output
                    output(EHRContract.EHR_contract_id, outputState1)
                    command(listOf(hospitalA.owningKey, municipalCouncil.owningKey), EHRContract.Commands.Admission())
                    failsWith("TOC not requested by this hospital")
                }
                //add output
                output(EHRContract.EHR_contract_id, outputState2)
                //add proper input
                input(EHRContract.EHR_contract_id, inputState3)
                command(listOf(hospitalB.owningKey, municipalCouncil.owningKey), EHRContract.Commands.Admission())
                verifies()

            }
        }
    }

    @Test
    fun updateCommandVerification() {
        val inputState1 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","ADMITTED", listOf(hospitalA,municipalCouncil))
        val outputState1 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1:testevent2","ADMITTED", listOf(hospitalA,municipalCouncil))

        ledgerServices.ledger {
            transaction {
                attachment(contractClassName = EHRContract.EHR_contract_id)
                tweak {
                    output(EHRContract.EHR_contract_id, outputState1)
                    //no input
                    command(hospitalA.owningKey, EHRContract.Commands.UpdateEHR())
                    failsWith("Transaction should have one input")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    //no output
                    command(hospitalA.owningKey, EHRContract.Commands.UpdateEHR())
                    failsWith("Transaction should have one output")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //check signers
                    command(hospitalA.owningKey, EHRContract.Commands.UpdateEHR())
                    failsWith("Request should be signed by the hospital and municipal council")
                }


                input(EHRContract.EHR_contract_id, inputState1)
                output(EHRContract.EHR_contract_id, outputState1)
                command(listOf(hospitalA.owningKey, municipalCouncil.owningKey), EHRContract.Commands.UpdateEHR())
                verifies()
            }
        }
    }

    @Test
    fun dischargeCommandVerification() {
        val inputState1 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","ADMITTED", listOf(hospitalA,municipalCouncil))
        val inputState2 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","DISCHARGED", listOf(hospitalA,municipalCouncil))
        val outputState1 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","ADMITTED", listOf(hospitalA,municipalCouncil))
        val outputState2 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","DISCHARGED", listOf(hospitalA,municipalCouncil))
        ledgerServices.ledger {
            transaction {
                attachment(contractClassName = EHRContract.EHR_contract_id)

                tweak {
                    output(EHRContract.EHR_contract_id, outputState1)
                    //no input
                    command(hospitalA.owningKey, EHRContract.Commands.Discharge())
                    failsWith("Transaction should have one input")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    //no output
                    command(hospitalA.owningKey, EHRContract.Commands.Discharge())
                    failsWith("Transaction should have one output")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //check signers
                    command(hospitalA.owningKey, EHRContract.Commands.Discharge())
                    failsWith("Request should be signed by the hospital and municipal council")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //check signers
                    command(listOf(hospitalA.owningKey, municipalCouncil.owningKey), EHRContract.Commands.Discharge())
                    failsWith("Discharge documents should be attached")
                }
                tweak {
                    //add file attachment
                    val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
                    val attachmentHash = attachment(inputStream)
                    attachment(attachmentId = attachmentHash)

                    input(EHRContract.EHR_contract_id, inputState2)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //check signers
                    command(listOf(hospitalA.owningKey, municipalCouncil.owningKey), EHRContract.Commands.Discharge())
                    failsWith("Patient needs to be admitted before discharging")
                }
                tweak {
                    //add file attachment
                    val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
                    val attachmentHash = attachment(inputStream)
                    attachment(attachmentId = attachmentHash)

                    input(EHRContract.EHR_contract_id, inputState1)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //check signers
                    command(listOf(hospitalA.owningKey, municipalCouncil.owningKey), EHRContract.Commands.Discharge())
                    failsWith("Output CareStatus should be DISCHARGED")
                }
                //add file attachment
                val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
                val attachmentHash = attachment(inputStream)
                attachment(attachmentId = attachmentHash)
                input(EHRContract.EHR_contract_id, inputState1)
                output(EHRContract.EHR_contract_id, outputState2)
                command(listOf(hospitalA.owningKey, municipalCouncil.owningKey), EHRContract.Commands.Discharge())
                verifies()
            }
        }
    }


    @Test
    fun requestTOCCommandVerification() {

        val inputState1 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","DISCHARGED", listOf(hospitalA,municipalCouncil))
        val inputState2 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","ADMITTED", listOf(hospitalA,municipalCouncil))
        val outputState1 = EHRState(municipalCouncil,hospitalA,hospitalB,"testid","testevent1","TRANSFER OF CARE REQUESTED", listOf(hospitalA,municipalCouncil))
        ledgerServices.ledger {
            transaction {
                attachment(contractClassName = EHRContract.EHR_contract_id)
                tweak {
                    output(EHRContract.EHR_contract_id, outputState1)
                    //no input
                    command(hospitalA.owningKey, EHRContract.Commands.RequestTransferOfCare())
                    failsWith("Transaction should have one input")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    //no output
                    command(hospitalA.owningKey, EHRContract.Commands.RequestTransferOfCare())
                    failsWith("Transaction should have one output")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //check signers
                    command(hospitalA.owningKey, EHRContract.Commands.RequestTransferOfCare())
                    failsWith("Request should be signed by both the hospital")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //wrong carestatus in input
                    command(listOf(hospitalA.owningKey, hospitalB.owningKey), EHRContract.Commands.RequestTransferOfCare())
                    failsWith("Transfer of care cannot be raised")
                }
                input(EHRContract.EHR_contract_id, inputState2)
                output(EHRContract.EHR_contract_id, outputState1)
                command(listOf(hospitalA.owningKey, hospitalB.owningKey), EHRContract.Commands.RequestTransferOfCare())
                verifies()
            }
        }
    }

    @Test
    fun reviewTOCCommandVerification() {
        val inputState1 = EHRState(municipalCouncil,hospitalA,null,"testid","testevent1","ADMITTED", listOf(hospitalA,municipalCouncil))
        val inputState2 = EHRState(municipalCouncil,hospitalA,hospitalB,"testid","testevent1","TRANSFER OF CARE REQUESTED", listOf(hospitalA,municipalCouncil))
        val outputState1 = EHRState(municipalCouncil,hospitalB,null,"testid","testevent","ADMITTED", listOf(hospitalB,municipalCouncil))
        val outputState2 = EHRState(municipalCouncil,hospitalB,null,"testid","testevent","TRANSFER OF CARE APPROVED", listOf(hospitalB,municipalCouncil))
        ledgerServices.ledger {
            transaction {
                attachment(contractClassName = EHRContract.EHR_contract_id)
                tweak {
                    output(EHRContract.EHR_contract_id, outputState1)
                    //no input
                    command(hospitalA.owningKey, EHRContract.Commands.ReviewTransferOfCare())
                    failsWith("Transaction should have one input")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    //no output
                    command(hospitalA.owningKey, EHRContract.Commands.ReviewTransferOfCare())
                    failsWith("Transaction should have one output")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //check signers
                    command(hospitalB.owningKey, EHRContract.Commands.ReviewTransferOfCare())
                    failsWith("Request should be signed by the new hospital and municipal council")
                }
                tweak {
                    input(EHRContract.EHR_contract_id, inputState1)
                    output(EHRContract.EHR_contract_id, outputState1)
                    //wrong carestatus in input
                    command(listOf(hospitalB.owningKey, municipalCouncil.owningKey), EHRContract.Commands.ReviewTransferOfCare())
                    failsWith("CareStatus should be of type Transfer of Care Requested")
                }
                input(EHRContract.EHR_contract_id, inputState2)
                //change output state
                output(EHRContract.EHR_contract_id, outputState2)
                command(listOf(hospitalB.owningKey, municipalCouncil.owningKey), EHRContract.Commands.ReviewTransferOfCare())
                verifies()
            }
        }
    }
}