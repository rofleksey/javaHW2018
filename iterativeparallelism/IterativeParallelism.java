package ru.ifmo.rain.borisov.iterativeparallelism;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.*;

public class IterativeParallelism implements ListIP {

    private ParallelMapper mapper;

    public static void main(String[] args) {
        // poshel est' sup
    }

    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    public IterativeParallelism() {
    }


    private <T> List<List<? extends T>> split(int n, List<? extends T> values) {
        ArrayList<List<? extends T>> parts = new ArrayList<>(n);
        int sub = values.size() / n;
        for (int i = 0; i < n - 1; i++) {
            parts.add(values.subList(i * sub, (i + 1) * sub));
        }
        //TODO: исправить if - это костыль
        if ((n - 1) * sub < values.size()) {
            parts.add(values.subList((n - 1) * sub, values.size()));
        }
        return parts;
    }

    private <R, T> List<R> getWorkersResults(List<List<? extends T>> list, FragmentOperationSupplier<R, T> opSupplier) throws InterruptedException {
        ArrayList<IterativeWorker<R, T>> workers = new ArrayList<>(list.size());
        ArrayList<R> results = new ArrayList<>(list.size());
        for (List<? extends T> l : list) {
            workers.add(new IterativeWorker<>(opSupplier.apply(l)));
        }
        for (IterativeWorker<R, T> w : workers) {
            w.start();
        }
        for (IterativeWorker<R, T> w : workers) {
            results.add(w.getResult());
        }
        return results;
    }

    private <R> R foldResults(List<R> results, BiFunction<R, R, R> f) {
        R r = results.get(0);
        for (R it : results.subList(1, results.size())) {
            r = f.apply(r, it);
        }
        return r;
    }

    private <R, T> List<R> parallelOperation(List<List<? extends T>> parts, FragmentOperationSupplier<R, T> opSupplier) throws InterruptedException {
        if (mapper == null) {
            return getWorkersResults(parts, opSupplier);
        } else {
            return mapper.map(it -> opSupplier.apply(it).get(), parts);
        }
    }

    private <T> T maxMinResult(int threads, List<? extends T> values, Comparator<? super T> comparator,
                               Predicate<Integer> special) throws InterruptedException {
        List<List<? extends T>> parts = split(Math.min(threads, values.size()), values);
        List<T> results = parallelOperation(parts, new FragmentOperationSupplier<>(() -> new ResultHolder<>(values.get(0)), (holder, t) -> {
            if (special.test(comparator.compare(holder.result, t))) {
                holder.result = t;
            }
        }));
        return foldResults(results, (a, b) -> special.test(comparator.compare(a, b)) ? b : a);
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        return maxMinResult(threads, values, comparator, a -> a < 0);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        return maxMinResult(threads, values, comparator, a -> a > 0);
    }

    private <T> boolean allAnyResult(int threads, List<? extends T> values, Predicate<? super T> predicate,
                                     Boolean def, Predicate<Boolean> test, BiFunction<Boolean, Boolean, Boolean> folder) throws InterruptedException {

        List<List<? extends T>> parts = split(Math.min(threads, values.size()), values);
        List<Boolean> results = parallelOperation(parts, new FragmentOperationSupplier<>(() -> new ResultHolder<>(def), (holder, t) -> {
            if (test.test(predicate.test(t))) {
                holder.result = !def;
            }
        }));
        return foldResults(results, folder);
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return allAnyResult(threads, values, predicate, true, a -> !a, (a, b) -> (a && b));
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return allAnyResult(threads, values, predicate, false, a -> a, (a, b) -> (a || b));
    }

    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        List<List<?>> parts = split(Math.min(threads, values.size()), values);
        List<StringBuilder> results = parallelOperation(parts, new FragmentOperationSupplier<>(() -> new ResultHolder<>(new StringBuilder()), (holder, t) -> {
            holder.result.append(t);
        }));
        return foldResults(results, StringBuilder::append).toString();
    }

    private <T, U> List<U> filterMapResult(int threads, List<? extends T> values,
                                           Predicate<? super T> predicate, Function<? super T, ? extends U> f) throws InterruptedException {

        List<List<? extends T>> parts = split(Math.min(threads, values.size()), values);
        List<LinkedList<U>> results = parallelOperation(parts, new FragmentOperationSupplier<>(() -> new ResultHolder<>(new LinkedList<U>()), (holder, t) -> {
            if (predicate.test(t)) {
                holder.result.add(f.apply(t));
            }
        }));
        return foldResults(results, (r1, r2) -> {
            r1.addAll(r2);
            return r1;
        });
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return filterMapResult(threads, values, predicate, a -> a);
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f) throws InterruptedException {
        return filterMapResult(threads, values, a -> true, f);
    }
}
