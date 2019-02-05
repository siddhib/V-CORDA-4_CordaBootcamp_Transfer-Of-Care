package com.kotlin_bootcamp


import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

data class EHRState(val municipalCouncil:Party,
               val hospital: Party,
               val transferToHospital: Party?,
               val ehrId: String,
               val medicalEvents: String,
               val careStatus: String,
               override val participants: List<AbstractParty> =listOf(municipalCouncil, hospital)) : QueryableState {


    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is EHRSchemaV1 -> EHRSchemaV1.PersistentEHR(
                    this.hospital.name.toString(),
                    this.transferToHospital.toString(),
                    this.ehrId,
                    this.medicalEvents,
                    this.careStatus
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(EHRSchemaV1)
}



