# ADR-007: Search Engine Selection

**Status**: Accepted (superseded in practice by PostgreSQL full-text search, see Addendum 2026-10-26)  
**Date**: 2025-02-20  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Development Team

---

## Context

The Award Monitoring & Tracking System requires advanced search capabilities for awards, documents, and user profiles. This includes full-text search, faceted search, and analytics for reporting features.

### Background
- Full-text search across award descriptions and documents
- Faceted search for filtering awards by criteria
- Search analytics for usage tracking and insights
- Support for multi-language content (English/Ukrainian)

---

## Decision

**Elasticsearch 8+** has been selected as the search engine for the Award Monitoring & Tracking System.

### Chosen Approach
- **Search Engine**: Elasticsearch 8+ (latest stable version)
- **Integration**: Spring Data Elasticsearch with Spring Boot
- **Deployment**: Single node for development, cluster for production
- **Use Cases**: Award search, document content search, analytics

### Rationale
- **Full-Text Search**: Advanced text analysis and scoring capabilities
- **Performance**: Fast search response times with indexing optimization
- **Multi-Language**: Excellent support for multiple languages and analyzers
- **Analytics**: Built-in aggregations for search analytics and reporting
- **Spring Integration**: Good Spring Data Elasticsearch support

---

## Consequences

### Positive Consequences
- **Search Performance**: Sub-second search response times
- **User Experience**: Advanced search features improve usability
- **Analytics**: Rich search analytics for insights and reporting
- **Scalability**: Horizontal scaling for large document volumes

### Negative Consequences
- **Resource Usage**: Significant memory and CPU requirements
- **Complexity**: Additional infrastructure component to manage
- **Learning Curve**: Elasticsearch query DSL and optimization

---

## Alternatives Considered

### Alternative 1: Apache Solr
- **Pros**: Strong configuration management, good documentation
- **Cons**: More complex setup, less cloud-native features
- **Reason for Rejection**: Elasticsearch better cloud integration

### Alternative 2: PostgreSQL Full-Text Search
- **Pros**: No additional infrastructure, simple setup
- **Cons**: Limited search features, poor scalability for complex queries
- **Reason for Rejection**: Insufficient for advanced search requirements

---

## Implementation Notes

### Technical Requirements
- **Elasticsearch Version**: 8.0+ for latest security and performance features
- **Spring Integration**: Spring Data Elasticsearch 5.0+
- **Memory**: 4GB heap minimum for production
- **Storage**: SSD recommended for optimal performance

### Index Configuration
```json
{
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "multilingual_analyzer"
      },
      "description": {
        "type": "text",
        "analyzer": "multilingual_analyzer"
      },
      "tags": {
        "type": "keyword"
      },
      "created_date": {
        "type": "date"
      }
    }
  }
}
```

### Search Strategy
- **Awards Index**: Award metadata and descriptions
- **Documents Index**: Document content and metadata
- **Users Index**: User profiles for admin search

---

## Success Metrics

- **Search Response Time**: < 50ms for 95th percentile
- **Search Relevance**: > 85% user satisfaction with results
- **Index Size**: < 10GB for initial dataset

---

## Related Documents

- **Tech Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-004 Database](./ADR-004-Database.md)
- **External Resources**: [Elasticsearch Documentation](https://www.elastic.co/guide/)

---

## Addendum 2026-10: Implementation deferred

Elasticsearch is not used by the application in Epics 1 and 2. Award lists, filters and the duplicate
check run on PostgreSQL (JPA specifications; `pg_trgm` similarity for possible duplicates); category suggestion matches
the `award_categories.keywords` arrays.

Elasticsearch appears only in the optional monitoring stack (`infra/docker-compose.monitoring.yml`) as the log
store (see ADR-011 addendum). Full-text award search is decided at the Epic 5 kickoff.

---

## Addendum 2026-10-05: PostgreSQL full-text search evaluated first

The Epic 5 kickoff evaluates PostgreSQL full-text search before Elasticsearch: a `tsvector` over the Ukrainian
and English titles, the awarding organisation and the description, with a GIN index, combined with the existing
`pg_trgm` similarity for typos. One university holds tens of thousands of awards at most, which PostgreSQL
searches in milliseconds, and the data stays in one store with the same access rules and backups. Elasticsearch
is chosen only if the evaluation shows a need PostgreSQL cannot meet (relevance tuning across languages, facets
over large volumes, cross-university search).

## Addendum 2026-10-26: PostgreSQL full-text search is the search engine

Feature 5.4 ships the search on PostgreSQL (migration V047). Two generated stored columns on `awards` hold the
searched texts: `search_vector` (`simple` configuration; titles weight A, awarding organisation B, descriptions C;
apostrophes removed) and `search_text` (lower-cased titles and awarding organisation), each with a GIN index
(`idx_awards_search`, `trgm_awards_search`). PostgreSQL 17 has no Ukrainian stemmer, so the query side cuts up to
two final vowels of each word and matches every stem as a prefix (`прем:* & шевченк:*`). On the achievements
lists the owner's name is matched as well. A trigram word similarity of at least 0.5 on the titles and awarding
organisation is the typo fallback, run only when no award matches the words.

Measured on 100 000 approved awards (story 5.4.1 scale test, PostgreSQL 17): a ranked two-word search with
1 666 matches takes about 200 ms in the database and answers under 1 s through the API on both lists; a typo
search through the trigram index takes 16 ms. The trigram match OR'd with the full-text match took 1.7 s for
common words (10 000 candidate rows rechecked), hence the fallback. Elasticsearch stays out unless one of the
triggers of the 2026-10-05 addendum arises.

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-20 | Stefan Kostyk | Initial version | Document creation |
| 2026-10-01 | Stefan Kostyk | Addendum: implementation deferred | Documentation sync after Epic 2 |
| 2026-10-05 | Stefan Kostyk | Addendum: PostgreSQL full-text search evaluated first | Design review of 2026-10-04 |
| 2026-10-26 | Stefan Kostyk | Addendum: PostgreSQL full-text search is the search engine | Feature 5.4 |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-20  
**ADR Category**: Technology 