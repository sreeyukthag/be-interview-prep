/**
 * Orders call the catalog's stock API synchronously rather than through events: the reservation has to run inside
 * the order's own transaction so that a failed item rolls back every item reserved before it.
 */
@ApplicationModule(
        displayName = "Orders",
        allowedDependencies = {"common", "catalog :: stock"})
package com.sreeyukthag.beinterviewprep.orders;

import org.springframework.modulith.ApplicationModule;
