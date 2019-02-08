package kotlin_bootcamp

import com.kotlin_bootcamp.*
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class ReviewTOCFlowTest{

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
        hospitalB = hospitalBNode.info.singleIdentity()
        municipalCouncil = municipalCouncilNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `If Rejected, Transaction is Signed by hospitalA and municipalCouncil`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,hospitalB,"xyz","Admitted to Hospital A","TRANSFER OF CARE REQUESTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= ReviewTOCFlow("xyz", "Reject")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()
        signedTx.verifyRequiredSignatures()
    }

    @Test
    fun `If Approved, Transaction is Signed by hospitalB and municipalCouncil`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,hospitalB,"xyz","Admitted to Hospital A","TRANSFER OF CARE REQUESTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= ReviewTOCFlow("xyz", "Approve")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()
        signedTx.verifyRequiredSignatures()
    }

    @Test
    fun `Fail if TOC request for given ehrID is not requested`(){
        val flow= ReviewTOCFlow("xyz", "Approve")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        assertFailsWith<FlowException> {future.resultFuture.getOrThrow()}
    }

    @Test
    fun `transferToHospital cannot be null`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,null,"xyz","Admitted to Hospital A","TRANSFER OF CARE REQUESTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= ReviewTOCFlow("xyz", "Approve")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        assertFailsWith<NullPointerException> {future.resultFuture.getOrThrow()}
    }

    @Test
    fun `If Approved, EHR state is not visible to old hospital`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,hospitalB,"xyz","Admitted to Hospital A","TRANSFER OF CARE REQUESTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= ReviewTOCFlow("xyz", "Approve")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()

        hospitalANode.database.transaction {
            val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal("xyz") }
            val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
            val vaultQueryCriteria = hospitalANode.services.vaultService.queryBy<EHRState>(criteria)

            assert(vaultQueryCriteria.states.isEmpty())
        }
    }

    @Test
    fun `If Approved, EHR state is visible to new hospital and municipalCouncil`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,hospitalB,"xyz","Admitted to Hospital A","TRANSFER OF CARE REQUESTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= ReviewTOCFlow("xyz", "Approve")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()

        hospitalBNode.database.transaction {
            val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal("xyz") }
            val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
            val vaultQueryCriteria = hospitalBNode.services.vaultService.queryBy<EHRState>(criteria)
            assert(vaultQueryCriteria.states.isNotEmpty())
        }
        municipalCouncilNode.database.transaction {
            val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal("xyz") }
            val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
            val vaultQueryCriteria = municipalCouncilNode.services.vaultService.queryBy<EHRState>(criteria)
            assert(vaultQueryCriteria.states.isNotEmpty())
        }
    }

    @Test
    fun `If Rejected, EHR state is not visible to new hospital`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,hospitalB,"xyz","Admitted to Hospital A","TRANSFER OF CARE REQUESTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= ReviewTOCFlow("xyz", "Reject")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()

        hospitalBNode.database.transaction {
            val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal("xyz") }
            val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
            val vaultQueryCriteria = hospitalBNode.services.vaultService.queryBy<EHRState>(criteria)
            assert(vaultQueryCriteria.states.isEmpty())
        }
    }

    @Test
    fun `If Rejected, EHR state is visible to old hospital and municipalCouncil`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,hospitalB,"xyz","Admitted to Hospital A","TRANSFER OF CARE REQUESTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= ReviewTOCFlow("xyz", "Reject")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()

        hospitalANode.database.transaction {
            val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal("xyz") }
            val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
            val vaultQueryCriteria = hospitalANode.services.vaultService.queryBy<EHRState>(criteria)
            println("hospitalA output:"+vaultQueryCriteria.states)
            assert(vaultQueryCriteria.states.isNotEmpty())
        }
        municipalCouncilNode.database.transaction {
            val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal("xyz") }
            val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
            val vaultQueryCriteria = municipalCouncilNode.services.vaultService.queryBy<EHRState>(criteria)
            println("municipal output:"+vaultQueryCriteria.states)
            assert(vaultQueryCriteria.states.isNotEmpty())
        }
    }

    @Test
    fun `Fail if status is not Approve or Reject`(){
        val dummyState = EHRState(municipalCouncil,hospitalA,hospitalB,"xyz","Admitted to Hospital A","TRANSFER OF CARE REQUESTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= ReviewTOCFlow("xyz", "hgfvjlhl")
        val future=municipalCouncilNode.services.startFlow(flow)
        mockNet.runNetwork()
        assertFailsWith<FlowException> {future.resultFuture.getOrThrow()}
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