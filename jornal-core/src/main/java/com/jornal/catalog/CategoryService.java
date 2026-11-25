package com.jornal.catalog;

import com.jornal.catalog.dto.*;
import com.jornal.catalog.enums.ErrorCatalog;
import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import com.jornal.shared.ApiException;
import com.jornal.shared.Locales;
import com.jornal.shared.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService implements CatalogApi {

    private static final int PUBLIC_LIMIT = 20;

    private final CategoryRepository repository;
    private final CategoryMapper mapper;
    private final CategoryTranslationRepository translationRepository;
    private final CategoryPriceRepository priceRepository;
    private final RegionApi regionApi;
    private final Clock clock;
    private final CatalogCache cache;

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> findAll(Boolean active, String q, Pageable pageable) {
        return PageResponse.from(
                repository.findAll(active, toPattern(q), pageable).map(mapper::toResponse));
    }

    /**
     * Categorías activas en el idioma de la petición, con el precio de esta región. Se sirven desde la caché de
     * Redis ({@link CatalogCache}) por región, idioma y texto buscado; cualquier cambio del administrador la invalida.
     */
    @Transactional(readOnly = true)
    public List<CategoryView> findActive(String q) {
        RegionProfileView profile = regionApi.current();
        List<String> chain = localeChain(profile);
        return cache.active(chain.getFirst(), q, () -> loadActive(q, profile, chain));
    }

    private List<CategoryView> loadActive(String q, RegionProfileView profile, List<String> chain) {
        List<Category> categories = repository.searchActive(toPattern(q), PUBLIC_LIMIT);
        if (categories.isEmpty()) return List.of();

        Map<UUID, CategoryTranslation> translations = bestTranslations(ids(categories), chain);
        Map<UUID, CategoryPrice> prices = priceRepository
                .findByCategoryIdInAndRegionCode(ids(categories), profile.code()).stream()
                .collect(Collectors.toMap(CategoryPrice::getCategoryId, Function.identity()));

        Collator collator = Collator.getInstance(Locales.toLocale(chain.getFirst()));
        return categories.stream()
                .map(c -> toView(c, translations.get(c.getId()), prices.get(c.getId()), profile))
                .sorted(Comparator.comparing(CategoryView::name, collator))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public CategoryResponse save(CategoryRequest request) {
        if (repository.existsBySlug(request.slug())) {
            throw new ApiException(ErrorCatalog.CAT002_SLUG_ALREADY_EXISTS, request.slug());
        }
        Category category = mapper.toEntity(request);
        cache.invalidate();
        return mapper.toResponse(repository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = getOrThrow(id);
        mapper.update(request, category);
        cache.invalidate();
        return mapper.toResponse(category);
    }

    @Transactional
    public void deactivate(UUID id) {
        getOrThrow(id).setActive(false);
        cache.invalidate();
    }

    @Transactional
    public void activate(UUID id) {
        getOrThrow(id).setActive(true);
        cache.invalidate();
    }

    /** Resúmenes en el idioma de la petición (los usan otros módulos, por ejemplo workers). */
    @Override
    @Transactional(readOnly = true)
    public List<CategorySummary> findActiveByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Category> categories = repository.findByIdInAndActiveTrue(ids);
        Map<UUID, CategoryTranslation> translations =
                bestTranslations(ids(categories), localeChain(regionApi.current()));
        return categories.stream()
                .map(c -> {
                    CategoryTranslation t = translations.get(c.getId());
                    return new CategorySummary(c.getId(), c.getSlug(), t != null ? t.getName() : c.getName());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategoryPriceSummary> priceOf(UUID categoryId) {
        RegionProfileView profile = regionApi.current();
        return repository.findById(categoryId)
                .filter(Category::isActive)
                .flatMap(c -> priceRepository.findByCategoryIdAndRegionCode(c.getId(), profile.code())
                        .map(p -> {
                            CategoryTranslation t = bestTranslations(List.of(c.getId()), localeChain(profile))
                                    .get(c.getId());
                            return new CategoryPriceSummary(c.getId(), c.getSlug(),
                                    t != null ? t.getName() : c.getName(),
                                    p.getUnit(), p.getPriceMinor(), p.getCurrency());
                        }));
    }

    @Transactional
    public void upsertTranslation(UUID categoryId, String locale, TranslationRequest req) {
        if (!Locales.isSupported(locale)) {
            throw new ApiException(ErrorCatalog.CAT003_LOCALE_NOT_SUPPORTED);
        }
        getOrThrow(categoryId);
        CategoryTranslation t = translationRepository.findByCategoryIdAndLocale(categoryId, locale)
                .orElseGet(() -> {
                    var n = new CategoryTranslation();
                    n.setCategoryId(categoryId);
                    n.setLocale(locale);
                    return n;
                });
        t.setName(req.name().trim());
        t.setDescription(req.description());
        translationRepository.save(t);
        cache.invalidate();
    }

    /** Fija el precio de la categoría en la región de esta instancia, con la moneda del perfil. */
    @Transactional
    public CategoryView.Price upsertPrice(UUID categoryId, PriceRequest request) {
        getOrThrow(categoryId);
        RegionProfileView profile = regionApi.current();
        CategoryPrice price = priceRepository.findByCategoryIdAndRegionCode(categoryId, profile.code())
                .orElseGet(() -> {
                    CategoryPrice created = new CategoryPrice();
                    created.setCategoryId(categoryId);
                    created.setRegionCode(profile.code());
                    return created;
                });
        price.setUnit(request.unit());
        price.setPriceMinor(request.amountMinor());
        price.setCurrency(profile.currency());
        price.setUpdatedAt(clock.instant());
        priceRepository.save(price);
        cache.invalidate();
        return toPrice(price, profile);
    }

    // ------------------------------------------------------------------ internos

    /** Idioma pedido → idioma del perfil → en-US, sin repetidos. */
    private static List<String> localeChain(RegionProfileView profile) {
        String requested = LocaleContextHolder.getLocale().toLanguageTag();
        LinkedHashSet<String> chain = new LinkedHashSet<>();
        if (Locales.isSupported(requested)) chain.add(requested);
        if (Locales.isSupported(profile.defaultLocale())) chain.add(profile.defaultLocale());
        chain.add(Locales.FALLBACK);
        return List.copyOf(chain);
    }

    private Map<UUID, CategoryTranslation> bestTranslations(Collection<UUID> ids, List<String> chain) {
        Map<UUID, CategoryTranslation> best = new HashMap<>();
        for (CategoryTranslation t : translationRepository.findByCategoryIdInAndLocaleIn(ids, chain)) {
            CategoryTranslation current = best.get(t.getCategoryId());
            if (current == null || chain.indexOf(t.getLocale()) < chain.indexOf(current.getLocale())) {
                best.put(t.getCategoryId(), t);
            }
        }
        return best;
    }

    private static CategoryView toView(Category c, CategoryTranslation t, CategoryPrice p, RegionProfileView profile) {
        return new CategoryView(c.getId(), c.getSlug(),
                t != null ? t.getName() : c.getName(),
                t != null ? t.getDescription() : c.getDescription(),
                t != null ? t.getLocale() : null,
                p != null ? toPrice(p, profile) : null);
    }

    private static CategoryView.Price toPrice(CategoryPrice p, RegionProfileView profile) {
        return new CategoryView.Price(p.getUnit(), p.getPriceMinor(), p.getCurrency(), profile.moneyScale());
    }

    private static List<UUID> ids(List<Category> categories) {
        return categories.stream().map(Category::getId).toList();
    }

    private Category getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCatalog.CAT001_CATEGORY_NOTFOUND, id));
    }

    private static String toPattern(String q) {
        if (q == null || q.isBlank()) {
            return "%";                                  // sin texto: trae todo
        }
        String clean = q.trim();
        if (clean.length() > 60) {
            clean = clean.substring(0, 60);              // límite razonable
        }
        clean = clean.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")                   // escapa los comodines para que
                .replace("%", "\\%")                     // un "%" o "_" escrito por el usuario
                .replace("_", "\\_");                    // se busque como texto literal
        return "%" + clean + "%";
    }
}
