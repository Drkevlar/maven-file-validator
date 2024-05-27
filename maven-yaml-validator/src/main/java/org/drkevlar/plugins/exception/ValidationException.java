

import java.io.IOException;


@Generated
public class ValidateException extends RuntimeException{

	public ValidateException(final Throwable cause) {
		super(cause);
	}

	public static void of(final IOException e) {
		throw new ValidateException(e);
	}

	public static void of(final String message) {
		throw new ValidateException(message);
	}

	public ValidateException(final String message) {
		super(message);
	}
}
