package ru.ifmo.rain.borisov.iterativeparallelism;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class FragmentOperation<R, T> implements Supplier<R> {
    private List<? extends T> data;
    private final ResultHolder<R> resultHolder;
    private BiConsumer<ResultHolder<R>, T> func;

    FragmentOperation(ResultHolder<R> def, List<? extends T> data, BiConsumer<ResultHolder<R>, T> func) {
        this.resultHolder = def;
        this.data = data;
        this.func = func;
    }

    R getResult() {
        return resultHolder.result;
    }

    @Override
    public R get() {
        for (T aData : data) {
            this.func.accept(this.resultHolder, aData);
        }
        return getResult();
    }
}
