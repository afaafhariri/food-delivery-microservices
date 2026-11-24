package com.quickbite.delivery.service;

/**
 * Spring application event: a driver may have become free, or an order joined the waiting
 * list. Once the transaction that published it commits, {@link WaitingOrderDispatcher} tries
 * to give waiting orders a driver.
 *
 * @param reason what happened, for the log
 */
public record RetryWaitingOrders(String reason) {
}
