package com.philipxjm.neiratecalc.calc;

/** How a machine overclocks recipes. */
public enum OCKind {
    /** 4x power for 2x speed per tier. */
    NORMAL,
    /** 4x power for 4x speed per tier. */
    PERFECT,
    /** EBF-style: heat discount + perfect OC per 1800K of excess coil heat. */
    HEAT,
    /** Machine never overclocks. */
    NONE
}
