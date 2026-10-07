package com.sreeyukthag.beinterviewprep.orders.service;

import java.util.UUID;

/** Who is asking: a customer reaches only their own orders, an ADMIN reaches every order. */
public record OrderAccess(UUID customerId, boolean admin) {}
