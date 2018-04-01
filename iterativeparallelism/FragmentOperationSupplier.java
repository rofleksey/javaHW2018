package ru.ifmo.rain.borisov.iterativeparallelism;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class FragmentOperationSupplier<R, T> implements Function<List<? extends T>, FragmentOperation<? extends R, ? extends T>> {
    private Supplier<ResultHolder<R>> sup;
    private BiConsumer<ResultHolder<R>, T> consumer;

    FragmentOperationSupplier(Supplier<ResultHolder<R>> sup, BiConsumer<ResultHolder<R>, T> consumer) {
        this.sup = sup;
        this.consumer = consumer;
    }

    @Override
    public FragmentOperation<R, T> apply(List<? extends T> data) {
        return new FragmentOperation<>(sup.get(), data, consumer);
    }
}
