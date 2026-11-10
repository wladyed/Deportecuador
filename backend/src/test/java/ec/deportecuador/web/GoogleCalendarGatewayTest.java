package ec.deportecuador.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GoogleCalendarGatewayTest {
 GoogleCalendarGateway gateway;HttpClient client;AppointmentService.Slot slot;UUID key;
 @BeforeEach void setup(){var settings=mock(AssistantSettings.class);when(settings.read()).thenReturn(new AssistantSettings.Settings("593123456789",false,"test@example.org",true,"06:00","12:50",30,"1,2,3,4,5,6,7"));gateway=new GoogleCalendarGateway(new ObjectMapper(),settings);client=mock(HttpClient.class);ReflectionTestUtils.setField(gateway,"client",client);gateway.clientId="test";gateway.clientSecret="test";gateway.refreshToken="test";key=UUID.randomUUID();slot=new AppointmentService.Slot(UUID.randomUUID(),Instant.parse("2027-01-01T11:00:00Z"),Instant.parse("2027-01-01T11:30:00Z"),"OPEN",null);}
 @SuppressWarnings("unchecked") HttpResponse<String> response(int status,String body){HttpResponse<String> r=mock(HttpResponse.class);when(r.statusCode()).thenReturn(status);when(r.body()).thenReturn(body);return r;}
 @Test void freeBusyExcludesOccupiedIntervals()throws Exception{
  var tokenResponse=response(200,"{\"access_token\":\"test\"}");var busyResponse=response(200,"{\"calendars\":{\"test@example.org\":{\"busy\":[{\"start\":\"2027-01-01T11:10:00Z\",\"end\":\"2027-01-01T11:20:00Z\"}]}}}");when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(tokenResponse,busyResponse);assertTrue(gateway.available(List.of(slot)).isEmpty());
 }
 @Test void insertionUsesStableIdentifierAndDoesNotInviteExternalAttendees()throws Exception{
  var requests=new ArrayList<HttpRequest>();when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(i->{HttpRequest r=i.getArgument(0);requests.add(r);if(r.uri().getHost().equals("oauth2.googleapis.com"))return response(200,"{\"access_token\":\"test\"}");if(r.method().equals("GET"))return response(404,"");if(r.uri().getPath().endsWith("freeBusy"))return response(200,"{\"calendars\":{\"test@example.org\":{\"busy\":[]}}}");return response(201,"{}");});gateway.book(key,slot);assertEquals(4,requests.size());assertTrue(requests.get(1).uri().getPath().endsWith("de"+key.toString().replace("-","")));assertEquals("sendUpdates=none",requests.get(3).uri().getQuery());
 }
 @Test void googleErrorFailsClosed()throws Exception{var failure=response(403,"private upstream error");when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(failure);var e=assertThrows(ResponseStatusException.class,()->gateway.book(key,slot));assertEquals(503,e.getStatusCode().value());assertFalse(e.getMessage().contains("private upstream"));}
 @Test void missingCredentialsNeverMakesNetworkRequest(){gateway.refreshToken="";assertFalse(gateway.ready());assertTrue(gateway.available(List.of(slot)).isEmpty());assertThrows(ResponseStatusException.class,()->gateway.book(key,slot));verifyNoInteractions(client);}
}
