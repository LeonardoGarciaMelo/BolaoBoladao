package br.com.bolaoboladao.carteira.presentation.rest.filter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a resource whose identity is authenticated by its webhook signature
 * instead of the API Gateway user header.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WebhookAuthenticated {
}
