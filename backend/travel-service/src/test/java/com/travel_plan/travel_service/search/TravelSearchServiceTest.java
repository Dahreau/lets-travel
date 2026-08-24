package com.travel_plan.travel_service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

// ElasticsearchClient (interface) est mocke ; Hit/HitsMetadata/SearchResponse sont construits
// via leurs vrais Builders car non mockables proprement (voir troubleshooting.md #20).
@SuppressWarnings("unchecked")
class TravelSearchServiceTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final TravelSearchService service = new TravelSearchService(client);

    @Test
    void indexSendsTravelDocumentToTheTravelsIndex() throws IOException {
        Travel travel = travelWithDestination();
        when(client.index(any(Function.class))).thenReturn(mock(IndexResponse.class));

        service.index(travel);

        verify(client).index(any(Function.class));
    }

    @Test
    void indexWrapsIOExceptionInTravelSearchException() throws IOException {
        Travel travel = travelWithDestination();
        when(client.index(any(Function.class))).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.index(travel)).isInstanceOf(TravelSearchException.class);
    }

    @Test
    void deleteRemovesDocumentById() throws IOException {
        UUID id = UUID.randomUUID();
        when(client.delete(any(Function.class))).thenReturn(mock(DeleteResponse.class));

        service.delete(id);

        verify(client).delete(any(Function.class));
    }

    @Test
    void deleteWrapsIOExceptionInTravelSearchException() throws IOException {
        UUID id = UUID.randomUUID();
        when(client.delete(any(Function.class))).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(TravelSearchException.class);
    }

    @Test
    void searchReturnsIdsFromHitsInOrder() throws IOException {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(client.search(any(Function.class), org.mockito.ArgumentMatchers.eq(TravelDocument.class)))
                .thenReturn(searchResponseWithIds(firstId, secondId));

        List<UUID> ids = service.search("lisbon");

        assertThat(ids).containsExactly(firstId, secondId);
    }

    @Test
    void searchWrapsIOExceptionInTravelSearchException() throws IOException {
        when(client.search(any(Function.class), org.mockito.ArgumentMatchers.eq(TravelDocument.class)))
                .thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.search("lisbon")).isInstanceOf(TravelSearchException.class);
    }

    @Test
    void autocompleteReturnsIdsFromHits() throws IOException {
        UUID id = UUID.randomUUID();
        when(client.search(any(Function.class), org.mockito.ArgumentMatchers.eq(TravelDocument.class)))
                .thenReturn(searchResponseWithIds(id));

        List<UUID> ids = service.autocomplete("lis");

        assertThat(ids).containsExactly(id);
    }

    // Champs Builder obligatoires verifies sur le code source du client (tag v8.11.1) : Hit
    // (index, id), HitsMetadata (hits), SearchResponse (took, timedOut, shards, hits).
    private SearchResponse<TravelDocument> searchResponseWithIds(UUID... ids) {
        List<Hit<TravelDocument>> hits = java.util.Arrays.stream(ids)
                .map(id -> new Hit.Builder<TravelDocument>()
                        .index("travels")
                        .id(id.toString())
                        .build())
                .toList();

        return new SearchResponse.Builder<TravelDocument>()
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h.hits(hits))
                .build();
    }

    private Travel travelWithDestination() {
        Travel travel = Travel.builder()
                .id(UUID.randomUUID())
                .title("Iberian tour")
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 8))
                .status(TravelStatus.PLANNED)
                .price(new BigDecimal("450.00"))
                .currency("EUR")
                .build();
        Destination destination = Destination.builder()
                .travel(travel)
                .city("Lisbon")
                .country("Portugal")
                .orderIndex(0)
                .build();
        travel.getDestinations().add(destination);
        return travel;
    }
}
