package com.solr98.beyondintegration.feature.bind;

import java.util.UUID;

public interface IBindingTokenHolder {
    UUID getBindingToken();
    UUID resetBindingToken();
}
