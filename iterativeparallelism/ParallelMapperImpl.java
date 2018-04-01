package ru.ifmo.rain.borisov.iterativeparallelism;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {
    private final ParallelMapperWorker pool;

    public ParallelMapperImpl(int threads) {
        pool = new ParallelMapperWorker(threads);
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args) throws InterruptedException {
        ArrayList<ParallelMapperTask<R, T>> tasks = new ArrayList<>(args.size());
        ArrayList<R> result = new ArrayList<>(args.size());
        for (T t : args) {
            tasks.add(new ParallelMapperTask<>(f, t));
        }
        for (ParallelMapperTask t : tasks) {
            pool.addTask(t);
        }
        for (ParallelMapperTask<R, T> t : tasks) {
            result.add(t.getResult());
        }
        return result;
    }

    @Override
    public void close() {
        pool.stop();
    }
}
