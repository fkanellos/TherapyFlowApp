# Testing Strategy

## Philosophy
- Domain logic → Unit tests (fast, no DB)
- Repositories → Integration tests (real DB)
- API endpoints → API tests (Ktor testApplication)
- Google Calendar sync → Mock tests (never call real API in tests)

## Test Structure

```
test/kotlin/
  domain/
    PayrollCalculatorTest.kt
    GreekNameMatcherTest.kt
    AppointmentValidatorTest.kt
  data/
    AppointmentRepositoryTest.kt
    PayrollRepositoryTest.kt
  api/
    AppointmentRoutesTest.kt
    PayrollRoutesTest.kt
    AuthRoutesTest.kt
  fixtures/
    TestFixtures.kt       ← reusable test data
    TestDatabase.kt       ← in-memory H2 for tests
```

## Unit Test Example (Domain)

```kotlin
class GreekNameMatcherTest {
    
    private val clients = listOf(
        Client(id = uuid1, firstName = "Μαρία", lastName = "Παπαδοπούλου"),
        Client(id = uuid2, firstName = "Γιώργος", lastName = "Αρβανίτης"),
        Client(id = uuid3, firstName = "Ελένη", lastName = "Κωνσταντίνου"),
    )
    
    @Test
    fun `exact match works`() {
        val result = GreekNameMatcher.match("Μαρία Παπαδοπούλου", clients)
        assertIs<MatchResult.Exact>(result)
        assertEquals(uuid1, result.client.id)
    }
    
    @Test
    fun `accent-insensitive match works`() {
        val result = GreekNameMatcher.match("Μαρια Παπαδοπουλου", clients)
        assertIs<MatchResult.Exact>(result)
    }
    
    @Test
    fun `reversed name order works`() {
        // Greek calendar often has: Αρβανίτης Γιώργος
        val result = GreekNameMatcher.match("Αρβανίτης Γιώργος", clients)
        assertIs<MatchResult.Reversed>(result)
        assertEquals(uuid2, result.client.id)
    }
    
    @Test
    fun `surname-only match works when unique`() {
        val result = GreekNameMatcher.match("Κωνσταντίνου", clients)
        assertIs<MatchResult.SurnameOnly>(result)
        assertEquals(uuid3, result.client.id)
    }
    
    @Test
    fun `no match returns NoMatch`() {
        val result = GreekNameMatcher.match("Άγνωστος Τυχαίος", clients)
        assertIs<MatchResult.NoMatch>(result)
    }
}
```

## API Test Example

```kotlin
class AppointmentRoutesTest {
    
    @Test
    fun `create appointment returns 201`() = testApplication {
        application { configureTestApp() }
        
        val token = getTestToken(role = UserRole.THERAPIST)
        
        val response = client.post("/v1/appointments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "therapistId": "${TestFixtures.THERAPIST_ID}",
                    "clientId": "${TestFixtures.CLIENT_ID}",
                    "startTime": "2026-04-22T10:00:00Z",
                    "durationMinutes": 60,
                    "price": 80.00,
                    "sessionType": "INDIVIDUAL"
                }
            """)
        }
        
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<Map<String, Any>>()
        assertNotNull(body["data"])
    }
    
    @Test
    fun `therapist cannot see other therapist appointments`() = testApplication {
        application { configureTestApp() }
        
        val otherTherapistToken = getTestToken(
            role = UserRole.THERAPIST, 
            userId = TestFixtures.OTHER_THERAPIST_ID
        )
        
        val response = client.get("/v1/appointments/${TestFixtures.APPOINTMENT_ID}") {
            header(HttpHeaders.Authorization, "Bearer $otherTherapistToken")
        }
        
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
    
    @Test
    fun `owner can see all appointments`() = testApplication {
        application { configureTestApp() }
        
        val ownerToken = getTestToken(role = UserRole.OWNER)
        
        val response = client.get("/v1/appointments") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```

## Test Fixtures

```kotlin
object TestFixtures {
    val WORKSPACE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val THERAPIST_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val CLIENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val APPOINTMENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")
    val OTHER_THERAPIST_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000005")
    
    fun createTestWorkspace() = Workspace(
        id = WORKSPACE_ID,
        name = "Another Point of View",
        slug = "apov",
        plan = Plan.PRO,
        status = WorkspaceStatus.ACTIVE
    )
    
    fun createTestAppointment(
        therapistId: UUID = THERAPIST_ID,
        status: AppointmentStatus = AppointmentStatus.COMPLETED
    ) = Appointment(
        id = APPOINTMENT_ID,
        therapistId = therapistId,
        clientId = CLIENT_ID,
        startTime = Instant.parse("2026-04-22T10:00:00Z"),
        durationMinutes = 60,
        price = BigDecimal("80.00"),
        sessionType = SessionType.INDIVIDUAL,
        status = status,
        source = AppointmentSource.MANUAL
    )
}
```

## Coverage Requirements

- Domain layer: ≥ 90% coverage
- Data layer: ≥ 70% coverage
- API layer: all happy paths + main error cases
- Run: `./gradlew koverReport` to see coverage
