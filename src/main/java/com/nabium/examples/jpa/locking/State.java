package com.nabium.examples.jpa.locking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State of the United States.
 *
 * @see https://www.bls.gov/respondents/mwr/electronic-data-interchange/appendix-d-usps-state-abbreviations-and-fips-codes.htm
 * @see https://en.wikipedia.org/wiki/List_of_U.S._state_and_territory_abbreviations
 * @see https://en.wikipedia.org/wiki/List_of_regions_of_the_United_States
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "state")
public class State {

    /** Ansi 2-letter code, 2-letter postal abbreviations */
    @Id
    @Column(length = 2, nullable = false)
    private String id;

    @Column(length = 24, nullable = false, unique = true)
    private String name;

    /** ANSI 2-digit code */
    @Column(length = 2, nullable = false, unique = true)
    private String code;

    /** AP Stylebook abbreviations */
    @Column(length = 6, nullable = false, unique = true)
    private String abbr;

    /** Census Bureau-designated regions and divisions */
    @Enumerated(EnumType.STRING)
    @Column(name = "census_region", length = 9, nullable = false)
    private CensusRegion censusRegion;
}
