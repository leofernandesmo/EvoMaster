package org.evomaster.core.database.extract.postgres

import org.evomaster.client.java.controller.api.dto.database.schema.DatabaseType
import org.evomaster.client.java.controller.db.SqlScriptRunner
import org.evomaster.client.java.controller.internal.db.SchemaExtractor
import org.evomaster.core.database.DbActionTransformer
import org.evomaster.core.database.SqlInsertBuilder
import org.evomaster.core.search.gene.ArrayGene
import org.evomaster.core.search.gene.StringGene
import org.evomaster.core.search.gene.datetime.DateGene
import org.evomaster.core.search.gene.datetime.DateTimeGene
import org.evomaster.core.search.gene.datetime.TimeIntervalGene
import org.evomaster.core.search.gene.datetime.TimeGene
import org.evomaster.core.search.gene.sql.SqlUUIDGene
import org.evomaster.core.search.gene.textsearch.TextSearchQueryGene
import org.evomaster.core.search.gene.textsearch.TextSearchVectorGene
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Created by jgaleotti on 07-May-19.
 */
class UUIDTypeTest : ExtractTestBasePostgres() {

    override fun getSchemaLocation() = "/sql_schema/postgres_uuid_type.sql"


    @Test
    fun testUUIDType() {

        val schema = SchemaExtractor.extract(connection)

        assertNotNull(schema)

        assertEquals("public", schema.name.lowercase())
        assertEquals(DatabaseType.POSTGRES, schema.databaseType)

        val builder = SqlInsertBuilder(schema)
        val actions = builder.createSqlInsertionAction(
            "UUIDType", setOf(
                "uuidColumn"
            )
        )

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)

        assertTrue(genes[0] is SqlUUIDGene)

        val dbCommandDto = DbActionTransformer.transform(actions)
        SqlScriptRunner.execInsert(connection, dbCommandDto.insertions)

    }
}