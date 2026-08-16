package com.medilabo.integration.test;

import java.io.File;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class GatewayIT {

    @SuppressWarnings("resource")
    static ComposeContainer environment =
            new ComposeContainer(new File("../docker-compose.test.yml"))
                    .withExposedService("gateway", 8080,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));

    static String baseUrl;
    static String accessToken;

    @BeforeAll
    static void setup() {
        environment.start();

        String host = environment.getServiceHost("gateway", 8080);
        Integer port = environment.getServicePort("gateway", 8080);
        baseUrl = "http://" + host + ":" + port + "/api/v1";
        System.out.println("Gateway disponible sur : " + baseUrl);

        accessToken =
            given()
                .contentType("application/json")
                .body("""
                    {"username":"organisateur","password":"organisateur"}
                    """)
            .when()
                .post(baseUrl + "/auth/login")
            .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .extract().path("accessToken");
    }

    @AfterAll
    static void afterAll() {
        environment.stop();
    }

    private RequestSpecification authenticated() {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json");
    }

    private Integer createPatient(String firstName, String lastName, String dateOfBirth, String gender) {
        return authenticated()
                .body("""
                    {"firstName":"%s","lastName":"%s","dateOfBirth":"%s","gender":"%s"}
                    """.formatted(firstName, lastName, dateOfBirth, gender))
                .when()
                    .post(baseUrl + "/patient")
                .then()
                    .statusCode(201)
                    .extract().path("id");
    }

    private void addNote(Integer patientId, String note) {
        authenticated()
                .body("""
                    {"patId": %d, "note": "%s"}
                    """.formatted(patientId, note))
                .when()
                    .post(baseUrl + "/patient/note")
                .then()
                    .statusCode(201);
    }

    // ----------------- Authentification -----------------

    @Test
    void shouldRejectRequestWithoutToken() {
        given()
            .contentType("application/json")
            .body("""
                {"firstName":"toto","lastName":"test","dateOfBirth":"10/01/2000","gender":"M"}
                """)
        .when()
            .post(baseUrl + "/patient")
        .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        given()
            .contentType("application/json")
            .body("""
                {"username":"praticien","password":"mauvais_mdp"}
                """)
        .when()
            .post(baseUrl + "/auth/login")
        .then()
            .statusCode(401)
            .body("message", notNullValue());
    }

    // ----------------- CRUD Patient -----------------

    @Test
    void shouldCreatePatientThroughGateway() {
        authenticated()
            .body("""
                {"firstName":"toto","lastName":"test","dateOfBirth":"10/01/2000","gender":"M"}
                """)
        .when()
            .post(baseUrl + "/patient")
        .then()
            .statusCode(201)
            .body("firstName", equalTo("toto"));
    }

    @Test
    void shouldCreatePatientWithoutOptionalAddressAndPhone() {
        authenticated()
            .body("""
                {"firstName":"paul","lastName":"minimal","dateOfBirth":"10/01/1990","gender":"M"}
                """)
        .when()
            .post(baseUrl + "/patient")
        .then()
            .statusCode(201)
            .body("firstName", equalTo("paul"))
            .body("lastName", equalTo("minimal"));
    }

    @Test
    void shouldUpdatePatientWithValidData() {
        Integer patientId = createPatient("jean", "avant", "10/01/1985", "M");

        authenticated()
            .body("""
                {"firstName":"jean","lastName":"apres","dateOfBirth":"15/03/1985","gender":"M"}
                """)
        .when()
            .put(baseUrl + "/patient/" + patientId)
        .then()
            .statusCode(202)
            .body("lastName", equalTo("apres"));
    }

    @Test
    void shouldReturn400WhenUpdatingWithInvalidDateOfBirth() {
        Integer patientId = createPatient("jean", "test", "10/01/1985", "M");

        authenticated()
            .body("""
                {"firstName":"jean","lastName":"test","dateOfBirth":"01-02-2000","gender":"M"}
                """)
        .when()
            .put(baseUrl + "/patient/" + patientId)
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn400WhenUpdatingWithEmptyFirstNameAndLastName() {
        Integer patientId = createPatient("jean", "test", "10/01/1985", "M");

        authenticated()
            .body("""
                {"firstName":"","lastName":"","dateOfBirth":"10/01/1985","gender":"M"}
                """)
        .when()
            .put(baseUrl + "/patient/" + patientId)
        .then()
            .statusCode(400);
    }

    @Test
    void shouldDeletePatient() {
        Integer patientId = createPatient("jean", "asupprimer", "10/01/1985", "M");

        authenticated()
        .when()
            .delete(baseUrl + "/patient/" + patientId)
        .then()
            .statusCode(204);
    }

    // ----------------- Notes -----------------

    @Test
    void shouldAddNoteForPatient() {
        Integer patientId = createPatient("jean", "note", "10/01/1985", "M");

        authenticated()
            .body("""
                {"patId": %d, "note": "Consultation de routine"}
                """.formatted(patientId))
        .when()
            .post(baseUrl + "/patient/note")
        .then()
            .statusCode(201)
            .body("note", equalTo("Consultation de routine"));
    }

    @Test
    void shouldDeleteNotesForPatient() {
        Integer patientId = createPatient("jean", "notesupprime", "10/01/1985", "M");
        addNote(patientId, "Premiere note");
        addNote(patientId, "Deuxieme note");

        authenticated()
        .when()
            .delete(baseUrl + "/patient/note/" + patientId)
        .then()
            .statusCode(200)
            .body("deletedNotes", equalTo(2));
    }

    // ----------------- Risque diabète (cas de test officiels du sprint) -----------------

    @Test
    void shouldReturnNoneRiskForTestNonePatient() {
        Integer patientId = createPatient("Test", "TestNone", "31/12/1966", "F");

        addNote(patientId,
            "Le patient déclare qu'il 'se sent très bien' Poids égal ou inférieur au poids recommandé");

        authenticated()
        .when()
            .get(baseUrl + "/patient/risk/" + patientId)
        .then()
            .statusCode(200)
            .body("code", equalTo("NONE"));
    }

    @Test
    void shouldReturnBorderlineRiskForTestBorderlinePatient() {
        Integer patientId = createPatient("Test", "TestBorderline", "24/06/1945", "M");

        addNote(patientId,
            "Le patient déclare qu'il ressent beaucoup de stress au travail Il se plaint également que son audition est anormale dernièrement");
        addNote(patientId,
            "Le patient déclare avoir fait une réaction aux médicaments au cours des 3 derniers mois Il remarque également que son audition continue d'être anormale");

        authenticated()
        .when()
            .get(baseUrl + "/patient/risk/" + patientId)
        .then()
            .statusCode(200)
            .body("code", equalTo("BORDERLINE"));
    }

    @Test
    void shouldReturnInDangerRiskForTestInDangerPatient() {
        Integer patientId = createPatient("Test", "TestInDanger", "18/06/2004", "M");

        addNote(patientId,
            "Le patient déclare qu'il fume depuis peu");
        addNote(patientId,
            "Le patient déclare qu'il est fumeur et qu'il a cessé de fumer l'année dernière Il se plaint également de crises d'apnée respiratoire anormales Tests de laboratoire indiquant un taux de cholestérol LDL élevé");

        authenticated()
        .when()
            .get(baseUrl + "/patient/risk/" + patientId)
        .then()
            .statusCode(200)
            .body("code", equalTo("IN_DANGER"));
    }

    @Test
    void shouldReturnEarlyOnsetRiskForTestEarlyOnsetPatient() {
        Integer patientId = createPatient("Test", "TestEarlyOnset", "28/06/2002", "F");

        addNote(patientId,
            "Le patient déclare qu'il lui est devenu difficile de monter les escaliers Il se plaint également d'être essoufflé Tests de laboratoire indiquant que les anticorps sont élevés Réaction aux médicaments");
        addNote(patientId,
            "Le patient déclare qu'il a mal au dos lorsqu'il reste assis pendant longtemps");
        addNote(patientId,
            "Le patient déclare avoir commencé à fumer depuis peu Hémoglobine A1C supérieure au niveau recommandé");
        addNote(patientId,
            "Taille, Poids, Cholestérol, Vertige et Réaction");

        authenticated()
        .when()
            .get(baseUrl + "/patient/risk/" + patientId)
        .then()
            .statusCode(200)
            .body("code", equalTo("EARLY_ONSET"));
    }
}