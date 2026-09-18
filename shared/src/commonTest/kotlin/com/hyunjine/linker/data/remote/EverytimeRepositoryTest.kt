package com.hyunjine.linker.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EverytimeRepositoryTest {

    private val sampleXml = """<?xml version="1.0" encoding="UTF-8"?>
<response>
  <table year="2026" semester="2" status="1" identifier="MXbJmcQUOSymAb6IHAcE">
    <subject id="8001470">
      <internal value="13978006"/>
      <name value="C언어 (실시간화상강의)"/>
      <professor value="김창복"/>
      <time value="화8 ,수5 ,수6">
        <data day="1" starttime="192" endtime="204" place="화상강의강의실(가상)"/>
        <data day="2" starttime="156" endtime="180" place="화상강의강의실(가상)"/>
      </time>
      <place value="화상강의강의실(가상)"/>
      <credit value="3"/>
      <closed value="0"/>
    </subject>
    <subject id="8003418">
      <internal value="08296008"/>
      <name value="데이터베이스"/>
      <professor value="송상준"/>
      <time value="금7 ,금8 ,금9">
        <data day="4" starttime="180" endtime="216" place="AI관-307"/>
      </time>
      <place value="AI관-307"/>
      <credit value="3"/>
      <closed value="0"/>
    </subject>
  </table>
  <user name="추민교"/>
  <primaryTables>
    <primaryTable year="2026" semester="2" identifier="MXbJmcQUOSymAb6IHAcE"/>
    <primaryTable year="2026" semester="1" identifier="hc3wLtu1mLDNFysweEKd"/>
    <primaryTable year="2025" semester="2" identifier="HZZZHitqhg6WvnuW6A8I"/>
  </primaryTables>
</response>
""".trimIndent()

    @Test
    fun parses_sample_response_into_domain_model() {
        val result = EverytimeRepository.parseBody("MXbJmcQUOSymAb6IHAcE", sampleXml)
        assertEquals("추민교", result.ownerName)
        assertEquals(2026, result.year)
        assertEquals(2, result.semester)
        assertEquals("MXbJmcQUOSymAb6IHAcE", result.identifier)

        assertEquals(2, result.lectures.size)
        val c = result.lectures.first { it.id == "8001470" }
        assertEquals("C언어 (실시간화상강의)", c.name)
        assertEquals("김창복", c.professor)
        assertEquals(3, c.credit)
        assertEquals(2, c.slots.size)
        assertEquals(1, c.slots[0].day)
        assertEquals(192, c.slots[0].startSlot)
        assertEquals(204, c.slots[0].endSlot)
        assertEquals("화상강의강의실(가상)", c.slots[0].place)

        val db = result.lectures.first { it.id == "8003418" }
        assertEquals("AI관-307", db.slots[0].place)
        assertEquals(4, db.slots[0].day)
        assertEquals(180, db.slots[0].startSlot)
        assertEquals(216, db.slots[0].endSlot)

        assertEquals(3, result.availableSemesters.size)
        val recent = result.availableSemesters.first()
        assertEquals(2026, recent.year)
        assertEquals(2, recent.semester)
        assertTrue(result.availableSemesters.any { it.year == 2025 && it.semester == 2 })
    }

    @Test
    fun throws_on_failure_code_minus_one() {
        assertFailsWith<com.hyunjine.linker.data.remote.EverytimeException> {
            EverytimeRepository.parseBody("x", "<response>-1</response>")
        }
    }

    @Test
    fun throws_on_failure_code_minus_two() {
        val err = assertFailsWith<com.hyunjine.linker.data.remote.EverytimeException> {
            EverytimeRepository.parseBody("x", "<response>-2</response>")
        }
        assertEquals("친구만 볼 수 있는 시간표", err.message)
    }
}
