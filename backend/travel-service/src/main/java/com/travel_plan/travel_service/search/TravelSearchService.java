package com.travel_plan.travel_service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.travel_plan.travel_service.domain.Travel;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

// Recherche + autocomplete Elasticsearch (enonce : "Elasticsearch-based travel search with
// autocomplete for smooth, dynamic querying across all travel details"). Un seul index
// ("travels"), mapping dynamique par defaut d'Elasticsearch (les champs de TravelDocument sont
// simples - text/keyword/numeric/date - la detection automatique suffit, pas besoin de definir
// un mapping explicite pour tenir le delai).
@Service
public class TravelSearchService {

    private static final String INDEX = "travels";
    private static final int SEARCH_SIZE = 50;
    private static final int AUTOCOMPLETE_SIZE = 8;

    private final ElasticsearchClient client;

    public TravelSearchService(ElasticsearchClient client) {
        this.client = client;
    }

    public void index(Travel travel) {
        try {
            client.index(i -> i.index(INDEX)
                    .id(travel.getId().toString())
                    .document(TravelDocument.from(travel)));
        } catch (IOException e) {
            throw new TravelSearchException("Failed to index travel " + travel.getId(), e);
        }
    }

    public void delete(UUID travelId) {
        try {
            client.delete(d -> d.index(INDEX).id(travelId.toString()));
        } catch (IOException e) {
            throw new TravelSearchException("Failed to remove travel " + travelId + " from the search index", e);
        }
    }

    // Recherche "a travers tous les details du voyage" : multi_match sur titre + villes + pays.
    public List<UUID> search(String queryText) {
        Query query = Query.of(q -> q.multiMatch(
                m -> m.query(queryText).fields("title", "cities", "countries")));
        return runQuery(query, SEARCH_SIZE);
    }

    // Autocomplete : match_bool_prefix (disponible nativement depuis Elasticsearch 7.2), pas
    // besoin d'un mapping/analyzer ngram dedie pour des suggestions "en tapant" pertinentes.
    public List<UUID> autocomplete(String prefixText) {
        Query query = Query.of(q -> q.multiMatch(m -> m.query(prefixText)
                .type(TextQueryType.BoolPrefix)
                .fields("title", "cities")));
        return runQuery(query, AUTOCOMPLETE_SIZE);
    }

    private List<UUID> runQuery(Query query, int size) {
        try {
            SearchResponse<TravelDocument> response =
                    client.search(s -> s.index(INDEX).size(size).query(query), TravelDocument.class);
            return response.hits().hits().stream()
                    .map(hit -> UUID.fromString(hit.id()))
                    .toList();
        } catch (IOException e) {
            throw new TravelSearchException("Search request failed", e);
        }
    }
}
