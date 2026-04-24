package resenkov.work.parkinguserservice.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
        super("Пользователь с таким email уже существует: " + email);
    }
}
