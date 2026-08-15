package ch.junto.auth;

import ch.junto.core.exception.ApiErrorCode;
import ch.junto.core.exception.ApiException;

/** Typed delivery failure that rolls back token issuance before reaching the API boundary. */
final class AuthEmailDeliveryException extends ApiException {

    AuthEmailDeliveryException() {
        super(ApiErrorCode.EMAIL_DELIVERY_FAILED,
            "We couldn't send your code. Please try again.", 503);
    }
}
