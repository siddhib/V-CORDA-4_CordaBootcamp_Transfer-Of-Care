package kotlin_bootcamp

import com.kotlin_bootcamp.EHRState
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.QueryableState
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals

class EHRStateTests{
    val hospitalA = TestIdentity(CordaX500Name("Hospital A", "", "GB")).party
    val municipalCouncil = TestIdentity(CordaX500Name("Municipal Council", "", "GB")).party

    @Test
    fun ehrStateHasFieldsOfCorrectTypeInConstructor() {
        //TokenState(alice, bob, 1)
        EHRState(municipalCouncil,hospitalA,null,"testid","testevent","ADMITTED", listOf(hospitalA,municipalCouncil))

    }

    @Test
    //fun tokenStateHasGettersForIssuerOwnerAndAmount() {
    fun ehrStateHasGettersFor() {
        var ehrState = EHRState(municipalCouncil,hospitalA,null,"testid","testevent","ADMITTED", listOf(hospitalA,municipalCouncil))
        assertEquals(hospitalA, ehrState.hospital)
        assertEquals(municipalCouncil, ehrState.municipalCouncil)
        assertEquals("testid", ehrState.ehrId)
    }

    @Test
    fun ehrStateImplementsQueryableState() {
        assert(EHRState(municipalCouncil,hospitalA,null,"testid","testevent","ADMITTED", listOf(hospitalA,municipalCouncil)) is QueryableState)
    }

    @Test
    fun ehrStateHasParticipantsHospitalAndMunicipalCouncil() {
        var ehrState = EHRState(municipalCouncil,hospitalA,null,"testid","testevent","ADMITTED", listOf(hospitalA,municipalCouncil))
        assert(ehrState.participants.size >= 2)
        assert(ehrState.participants.contains(hospitalA))
        assert(ehrState.participants.contains(municipalCouncil))
    }
}