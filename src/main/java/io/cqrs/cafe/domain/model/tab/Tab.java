package io.cqrs.cafe.domain.model.tab;

import io.cqrs.cafe.application.CloseTab;
import io.cqrs.cafe.application.MarkDrinksServed;
import io.cqrs.cafe.application.OpenTab;
import io.cqrs.cafe.application.PlaceOrder;
import io.cqrs.cafe.domain.model.Aggregate;
import io.cqrs.cafe.domain.model.DomainEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class Tab implements Aggregate {

    private DomainEventPublisher domainEventPublisher;
    private boolean open = false;
    private List<OrderItem> outstandingDrinks = new ArrayList<>();
    private Double servedItemsValue = 0.0;

    Tab(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
    }

    //
    // Handle Commands
    //

    void handle(OpenTab c) {
        TabOpened tabOpened = new TabOpened(
                c.getId(),
                c.getTableNumber(),
                c.getWaiter());

        domainEventPublisher.publish(tabOpened);
        apply(tabOpened);
    }

    void handle(PlaceOrder c) {
        if (!open) throw new TabNotOpen();

        List<OrderItem> drinks = c.getItems().stream()
                .filter(OrderItem::isDrink)
                .collect(Collectors.toList());

        if (!drinks.isEmpty()) {
            DrinksOrdered drinksOrdered = new DrinksOrdered(
                    c.getId(),
                    drinks);
            domainEventPublisher.publish(drinksOrdered);
            // TODO apply
        }

        List<OrderItem> food = c.getItems().stream()
                .filter(item -> !item.isDrink())
                .collect(Collectors.toList());
        if (!food.isEmpty()) {
            FoodOrdered foodOrdered = new FoodOrdered(
                    c.getId(),
                    food);
            domainEventPublisher.publish(foodOrdered);
            // TODO apply
        }
    }

    void handle(MarkDrinksServed c) {
        if (!areDrinksOutstanding(c.getMenuNumbers())) {
            throw new DrinksNotOutstanding();
        }

        DrinksServed drinksServed = new DrinksServed(c.getTabId(), c.getMenuNumbers());

        domainEventPublisher.publish(drinksServed);
        // TODO apply
    }

    private boolean areDrinksOutstanding(List<Integer> menuNumbers) {
        List<Integer> outstandingDrinkNumbers = this.outstandingDrinks.stream()
                .map(OrderItem::menuNumber)
                .collect(Collectors.toList());
        return outstandingDrinkNumbers.containsAll(menuNumbers);
    }

    void handle(CloseTab c) {
        Double tipValue = c.getAmountPaid() - servedItemsValue;
        TabClosed tabClosed = new TabClosed(c.getTabId(), c.getAmountPaid(), servedItemsValue, tipValue);

        domainEventPublisher.publish(tabClosed);
        // TODO apply
    }

    //
    // Apply Events
    //

    void apply(TabOpened e) {
        this.open = true;
    }

    void apply(DrinksOrdered e) {
        this.outstandingDrinks.addAll(e.getItems());
    }

    void apply(DrinksServed e) {
        for (Integer menuNumber : e.getMenuNumbers()) {
            OrderItem drink = this.outstandingDrinks.stream().filter(d -> d.menuNumber() == menuNumber).findFirst().get();
            this.servedItemsValue += drink.price();
            this.outstandingDrinks.remove(drink);
        }
    }
}
