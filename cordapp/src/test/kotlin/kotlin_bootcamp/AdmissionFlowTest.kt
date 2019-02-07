package kotlin_bootcamp

import com.google.common.base.Predicates.instanceOf
import com.google.common.collect.ImmutableList
import com.kotlin_bootcamp.AdmissionFlow
import com.kotlin_bootcamp.EHRContract
import com.kotlin_bootcamp.EHRState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class AdmissionFlowTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var notaryNode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var hospitalANode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var hospitalBNode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var municipalCouncilNode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var hospitalA: Party
    private lateinit var hospitalB: Party
    private lateinit var municipalCouncil: Party
    private lateinit var notary: Party

    @Before
    fun setup() {

        mockNet = InternalMockNetwork(cordappPackages = listOf("com.kotlin_bootcamp","net.corda.testing.contracts", "net.corda.core.internal"))
        notaryNode = mockNet.defaultNotaryNode
        hospitalANode = mockNet.createPartyNode(CordaX500Name("Hospital A", "London", "GB"))
        hospitalBNode = mockNet.createPartyNode(CordaX500Name("Hospital B", "London", "GB"))
        municipalCouncilNode = mockNet.createPartyNode(CordaX500Name("Municipal Council", "London", "GB"))
        notary = mockNet.defaultNotaryIdentity
        hospitalA = hospitalANode.info.singleIdentity()
        hospitalB = hospitalANode.info.singleIdentity()
        municipalCouncil = municipalCouncilNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `Transaction is Signed by hospitalA and municipalCouncil`(){
        val flow= AdmissionFlow(municipalCouncil,"xyz")
        val future=hospitalANode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()
        signedTx.verifyRequiredSignatures()
    }

    @Test
    fun `Transaction has zero input if the patient is new`(){
        val flow= AdmissionFlow(municipalCouncil,"xyz")
        val future=hospitalANode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()
        assert(signedTx.inputs.isEmpty())
    }

    @Test
    fun `Transaction has one input if the patient is not new`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,null,"xyz","Admitted to Hospital A","DISCHARGED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= AdmissionFlow(municipalCouncil,"xyz")
        val future=hospitalANode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()
        assert(signedTx.inputs.isNotEmpty())
    }

    @Test
    fun `A patient cannot be admitted twice to same hospital`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,null,"xyz","Admitted to Hospital A","ADMITTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= AdmissionFlow(municipalCouncil,"xyz")
        val future=hospitalANode.services.startFlow(flow)
        mockNet.runNetwork()
        assertFailsWith<FlowException> {future.resultFuture.getOrThrow()}
    }

    @Test
    fun `Admitted patient cannot be admitted again to another hospital`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,null,"xyz","Admitted to Hospital A","ADMITTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= AdmissionFlow(municipalCouncil,"xyz")
        val future=hospitalBNode.services.startFlow(flow)
        mockNet.runNetwork()
        assertFailsWith<FlowException> {future.resultFuture.getOrThrow()}
    }

    @Test
    fun `Patient can be admitted if TOC is approved`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,null,"xyz","Transfer of Care Approved","TRANSFER OF CARE APPROVED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= AdmissionFlow(municipalCouncil,"xyz")
        val future=hospitalANode.services.startFlow(flow)
        val result = future.resultFuture.getOrThrow()
        mockNet.runNetwork()
      //  assertEquals("ADMITTED", result.)
    }

    private fun makeTransactions(dummyState: EHRState): SignedTransaction {
        // Create a dummy transaction
        val dummyTransaction = TransactionBuilder(notary)
                .addOutputState(dummyState, EHRContract.EHR_contract_id)
                .addCommand(EHRContract.Commands.Admission(), listOf(hospitalA.owningKey,municipalCouncil.owningKey))
        val ptx = hospitalANode.services.signInitialTransaction(dummyTransaction)
        val ctx = municipalCouncilNode.services.addSignature(ptx, municipalCouncil.owningKey)

        hospitalANode.database.transaction {
            hospitalANode.services.recordTransactions(ctx)
        }
        municipalCouncilNode.database.transaction {
            municipalCouncilNode.services.recordTransactions(ctx)
        }
        return ctx
    }
}