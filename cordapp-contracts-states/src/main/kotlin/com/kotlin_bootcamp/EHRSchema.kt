package com.kotlin_bootcamp

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for EHRState.
 */
object EHRSchema

/**
 * A EHRState schema.
 */
object EHRSchemaV1 : MappedSchema(
        schemaFamily = EHRSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentEHR::class.java)) {
    @Entity
    @Table(name = "ehr_states")
    class PersistentEHR(
            @Column(name = "hospital")
            var hospital: String,

            @Column(name = "transferToHospital")
            var transferToHospital: String,

            @Column(name = "ehrId")
            var ehrId: String,

            @Column(name = "medicalEvents")
            var medicalEvents: String,

            @Column(name = "careStatus")
            var careStatus: String

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this( "", "", "" , "", "")
    }
}
