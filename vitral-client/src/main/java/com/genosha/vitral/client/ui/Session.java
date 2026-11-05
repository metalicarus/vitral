package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.Models.User;
import com.genosha.vitral.client.api.VitralApi;

/** A logged-in user and the API connection that carries their token. */
public record Session(VitralApi api, User user) {
}
