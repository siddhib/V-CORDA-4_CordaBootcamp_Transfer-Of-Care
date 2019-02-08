package kotlin_bootcamp

import com.kotlin_bootcamp.DischargeFlow
import com.kotlin_bootcamp.EHRContract
import com.kotlin_bootcamp.EHRSchemaV1
import com.kotlin_bootcamp.EHRState
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.InputStreamAndHash
import net.corda.core.node.services.queryBy
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

class DischargeFlowTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var notaryNode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var hospitalANode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var municipalCouncilNode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var hospitalA: Party
    private lateinit var municipalCouncil: Party
    private lateinit var notary: Party
    @Before
    fun setup() {
        mockNet = InternalMockNetwork(cordappPackages = listOf("com.kotlin_bootcamp", "net.corda.testing.contracts", "net.corda.core.internal"))
        notaryNode = mockNet.defaultNotaryNode
        hospitalANode = mockNet.createPartyNode(CordaX500Name("Hospital A", "London", "GB"))
        municipalCouncilNode = mockNet.createPartyNode(CordaX500Name("Municipal Council", "London", "GB"))
        notary = mockNet.defaultNotaryIdentity
        hospitalA = hospitalANode.info.singleIdentity()
        municipalCouncil = municipalCouncilNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `Transaction is signed by hospitalA and municipalCouncil`(){
        //add file attachment
        val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
        hospitalANode.database.transaction {
            hospitalANode.services.attachments.importAttachment(jar = inputStream)
        }
        //admit patient 'xyz
        val dummyState = EHRState(municipalCouncil,hospitalA,null,"xyz","Admitted to Hospital A","ADMITTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)

        val flow = DischargeFlow(municipalCouncil,"xyz",hash)
        val future=hospitalANode.services.startFlow(flow)
        mockNet.runNetwork()
        println(mockNet.nodes.toString())
        val signedTx=future.resultFuture.getOrThrow()
        signedTx.verifyRequiredSignatures()
    }

    @Test
    fun `Transaction fails if patient is not admitted before discharging`(){
        val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
        val flow= DischargeFlow(municipalCouncil,"xyz", hash)
        val future=hospitalANode.services.startFlow(flow)
        mockNet.runNetwork()
        assertFailsWith<FlowException> {future.resultFuture.getOrThrow()}
    }

    @Test
    fun `Transaction must have discharge documents attached`(){
        //add file attachment
        val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
        hospitalANode.database.transaction {
            hospitalANode.services.attachments.importAttachment(jar = inputStream)
        }
        val dummyState = EHRState(municipalCouncil,hospitalA,null,"xyz","Admitted to Hospital A","ADMITTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= DischargeFlow(municipalCouncil,"xyz", hash)
        val future=hospitalANode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()
        assert(signedTx.tx.attachments.size == 2)
    }

    @Test
    fun `Hospital cannot see EHR state after discharge`(){
        //add file attachment
        val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
        hospitalANode.database.transaction {
            hospitalANode.services.attachments.importAttachment(jar = inputStream)
        }
        val dummyState = EHRState(municipalCouncil,hospitalA,null,"xyz","Admitted to Hospital A","ADMITTED", listOf(municipalCouncil,hospitalA))
        val stx = makeTransactions(dummyState)
        val flow= DischargeFlow(municipalCouncil,"xyz", hash)
        val future=hospitalANode.services.startFlow(flow)
        mockNet.runNetwork()
        val signedTx=future.resultFuture.getOrThrow()

        val ccyIndex = builder { EHRSchemaV1.PersistentEHR::ehrId.equal("xyz") }
        val criteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
        hospitalANode.database.transaction {
            assert(hospitalANode.services.vaultService.queryBy<EHRState>(criteria).states.isEmpty())
        }
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