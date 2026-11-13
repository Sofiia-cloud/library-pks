package ru.mirea.library.service;

import org.springframework.stereotype.Service;

import ru.mirea.library.dto.*;
import ru.mirea.library.error.*;

import java.util.*;

@Service
public class BulkService {
    private final BookRequestService service;

    public BulkService(BookRequestService service) {
        this.service = service;
    }

    // No outer transaction: each proxied service call commits or rolls back one item.
    public BulkResult apply(BulkRequest b) {
        if (b.items().stream().map(VersionedId::id).distinct().count() != b.items().size())
            throw new BusinessException("UUID в списке повторяются");
        if (b.action() == ru.mirea.library.model.BulkAction.STATUS && b.status() == null)
            throw new BusinessException("Выберите новый статус");
        if (b.action() == ru.mirea.library.model.BulkAction.ADD_TAG
                || b.action() == ru.mirea.library.model.BulkAction.REMOVE_TAG) {
            if (b.tag() == null || b.tag().isBlank()) throw new BusinessException("Укажите тег");
            BookRequestService.normalizeTags(Set.of(b.tag()));
        }
        var results = new ArrayList<BulkResult.Item>();
        for (var item : b.items()) {
            try {
                var changed =
                        switch (b.action()) {
                            case ARCHIVE -> service.delete(item.id(), item.version());
                            case RESTORE -> service.restore(item.id(), item.version());
                            case STATUS ->
                                    service.changeStatus(item.id(), item.version(), b.status());
                            case ADD_TAG ->
                                    service.changeTag(item.id(), item.version(), b.tag(), true);
                            case REMOVE_TAG ->
                                    service.changeTag(item.id(), item.version(), b.tag(), false);
                        };
                results.add(
                        new BulkResult.Item(
                                item.id(), true, 200, "Выполнено", changed.getVersion()));
            } catch (EntityNotFoundException e) {
                results.add(new BulkResult.Item(item.id(), false, 404, e.getMessage(), null));
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException
                    | org.springframework.dao.DataIntegrityViolationException e) {
                results.add(
                        new BulkResult.Item(
                                item.id(),
                                false,
                                409,
                                "Запись изменена или конфликтует с другими данными",
                                null));
            } catch (BusinessException | IllegalArgumentException e) {
                results.add(
                        new BulkResult.Item(
                                item.id(),
                                false,
                                400,
                                e instanceof BusinessException
                                        ? e.getMessage()
                                        : "Проверьте параметры операции",
                                null));
            }
        }
        int ok = (int) results.stream().filter(BulkResult.Item::success).count();
        return new BulkResult(results, ok, results.size() - ok);
    }
}
