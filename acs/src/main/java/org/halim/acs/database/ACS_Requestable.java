package org.halim.acs.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public method an actor exposes to the outside, and gives its code: the int a request starts
 * with, and the place of its line in {@link ACS_Actor#getRequestables}. A class's codes run from 0 to its
 * count less one, in the order its {@code request} switches on them, so the code is the method's whole
 * address on the wire and no name crosses it.
 * */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ACS_Requestable {

int value();

}
