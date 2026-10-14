package com.pmplatform.procurement.tender;

/** Which sealed part of a bid a file belongs to. */
public enum Envelope {
    /** Single-envelope tenders: technical and financial together. */
    SINGLE,
    TECHNICAL,
    FINANCIAL
}
