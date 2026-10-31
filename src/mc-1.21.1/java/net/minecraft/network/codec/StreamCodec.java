package net.minecraft.network.codec;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B object) {
                return decoder.decode(object);
            }

            @Override
            public void encode(B object, V object2) {
                encoder.encode(object, object2);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(StreamMemberEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B object) {
                return decoder.decode(object);
            }

            @Override
            public void encode(B object, V object2) {
                encoder.encode(object2, object);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(V expectedValue) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B object) {
                return expectedValue;
            }

            @Override
            public void encode(B object, V object2) {
                if (!object2.equals(expectedValue)) {
                    throw new IllegalStateException("Can't encode '" + object2 + "', expected '" + expectedValue + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(StreamCodec.CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> factory, Function<? super O, ? extends V> getter) {
        return new StreamCodec<B, O>() {
            @Override
            public O decode(B object) {
                return (O)factory.apply(StreamCodec.this.decode(object));
            }

            @Override
            public void encode(B object, O object2) {
                StreamCodec.this.encode(object, (V)getter.apply(object2));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(Function<O, ? extends B> bufferFactory) {
        return new StreamCodec<O, V>() {
            public V decode(O byteBuf) {
                B object = (B)bufferFactory.apply(byteBuf);
                return StreamCodec.this.decode(object);
            }

            public void encode(O byteBuf, V object) {
                B object2 = (B)bufferFactory.apply(byteBuf);
                StreamCodec.this.encode(object2, object);
            }
        };
    }

    default <U> StreamCodec<B, U> dispatch(
        Function<? super U, ? extends V> keyGetter, Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codecGetter
    ) {
        return new StreamCodec<B, U>() {
            @Override
            public U decode(B object) {
                V object2 = StreamCodec.this.decode(object);
                StreamCodec<? super B, ? extends U> streamCodec = (StreamCodec<? super B, ? extends U>)codecGetter.apply(object2);
                return (U)streamCodec.decode(object);
            }

            @Override
            public void encode(B object, U object2) {
                V object3 = (V)keyGetter.apply(object2);
                StreamCodec<B, U> streamCodec = (StreamCodec<B, U>)codecGetter.apply(object3);
                StreamCodec.this.encode(object, object3);
                streamCodec.encode(object, object2);
            }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec, Function<C, T1> getter, Function<T1, C> factory) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B object) {
                T1 object2 = codec.decode(object);
                return factory.apply(object2);
            }

            @Override
            public void encode(B object, C object2) {
                codec.encode(object, getter.apply(object2));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1, Function<C, T1> getter1, StreamCodec<? super B, T2> codec2, Function<C, T2> getter2, BiFunction<T1, T2, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                return factory.apply(object2, object3);
            }

            @Override
            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
            }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> getter1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> getter2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> getter3,
        Function3<T1, T2, T3, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                T3 object4 = codec3.decode(object);
                return factory.apply(object2, object3, object4);
            }

            @Override
            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
                codec3.encode(object, getter3.apply(object2));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> getter1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> getter2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> getter3,
        StreamCodec<? super B, T4> codec4,
        Function<C, T4> getter4,
        Function4<T1, T2, T3, T4, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                T3 object4 = codec3.decode(object);
                T4 object5 = codec4.decode(object);
                return factory.apply(object2, object3, object4, object5);
            }

            @Override
            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
                codec3.encode(object, getter3.apply(object2));
                codec4.encode(object, getter4.apply(object2));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> getter1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> getter2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> getter3,
        StreamCodec<? super B, T4> codec4,
        Function<C, T4> getter4,
        StreamCodec<? super B, T5> codec5,
        Function<C, T5> getter5,
        Function5<T1, T2, T3, T4, T5, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                T3 object4 = codec3.decode(object);
                T4 object5 = codec4.decode(object);
                T5 object6 = codec5.decode(object);
                return factory.apply(object2, object3, object4, object5, object6);
            }

            @Override
            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
                codec3.encode(object, getter3.apply(object2));
                codec4.encode(object, getter4.apply(object2));
                codec5.encode(object, getter5.apply(object2));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> getter1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> getter2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> getter3,
        StreamCodec<? super B, T4> codec4,
        Function<C, T4> getter4,
        StreamCodec<? super B, T5> codec5,
        Function<C, T5> getter5,
        StreamCodec<? super B, T6> codec6,
        Function<C, T6> getter6,
        Function6<T1, T2, T3, T4, T5, T6, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B object) {
                T1 object2 = codec1.decode(object);
                T2 object3 = codec2.decode(object);
                T3 object4 = codec3.decode(object);
                T4 object5 = codec4.decode(object);
                T5 object6 = codec5.decode(object);
                T6 object7 = codec6.decode(object);
                return factory.apply(object2, object3, object4, object5, object6, object7);
            }

            @Override
            public void encode(B object, C object2) {
                codec1.encode(object, getter1.apply(object2));
                codec2.encode(object, getter2.apply(object2));
                codec3.encode(object, getter3.apply(object2));
                codec4.encode(object, getter4.apply(object2));
                codec5.encode(object, getter5.apply(object2));
                codec6.encode(object, getter6.apply(object2));
            }
        };
    }

    static <B, T> StreamCodec<B, T> recursive(UnaryOperator<StreamCodec<B, T>> modifier) {
        return new StreamCodec<B, T>() {
            private final Supplier<StreamCodec<B, T>> inner = Suppliers.memoize(() -> modifier.apply(this));

            @Override
            public T decode(B object) {
                return this.inner.get().decode(object);
            }

            @Override
            public void encode(B object, T object2) {
                this.inner.get().encode(object, object2);
            }
        };
    }

    default <S extends B> StreamCodec<S, V> cast() {
        return this;
    }

    @FunctionalInterface
    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> var1);
    }
}
