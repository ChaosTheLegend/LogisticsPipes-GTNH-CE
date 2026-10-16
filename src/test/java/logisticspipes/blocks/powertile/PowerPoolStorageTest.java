package logisticspipes.blocks.powertile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class PowerPoolStorageTest {

    private static final long CAP = 2_000_000;

    private static final class Junction implements PowerPoolStorage.Member {

        PowerPoolStorage pool;

        @Override
        public PowerPoolStorage pool() {
            return pool;
        }

        @Override
        public void setPool(PowerPoolStorage pool) {
            this.pool = pool;
        }

        @Override
        public long poolCapacity() {
            return CAP;
        }
    }

    private static Junction junction(long energy) {
        Junction j = new Junction();
        j.setPool(PowerPoolStorage.single(j, energy));
        return j;
    }

    @Test
    void mergingAddsCapacityAndEnergy() {
        Junction a = junction(1000);
        Junction b = junction(3000);
        PowerPoolStorage pool = PowerPoolStorage.bind(Arrays.asList(a, b));
        assertEquals(2 * CAP, pool.getCapacity());
        assertEquals(4000, pool.getStored());
        assertSame(pool, a.pool());
        assertSame(pool, b.pool());
        // the same members bind to the same pool
        assertSame(pool, PowerPoolStorage.bind(Arrays.asList(b, a)));
    }

    @Test
    void splittingSharesEnergyByCapacity() {
        Junction a = junction(0);
        Junction b = junction(0);
        Junction c = junction(0);
        PowerPoolStorage pool = PowerPoolStorage.bind(Arrays.asList(a, b, c));
        pool.addEnergy(9000);
        PowerPoolStorage ab = PowerPoolStorage.bind(Arrays.asList(a, b));
        PowerPoolStorage alone = PowerPoolStorage.bind(Collections.singletonList(c));
        assertNotSame(pool, ab);
        assertEquals(6000, ab.getStored());
        assertEquals(3000, alone.getStored());
        assertEquals(2 * CAP, ab.getCapacity());
    }

    @Test
    void drawsComeFromThePool() {
        Junction a = junction(10);
        Junction b = junction(0);
        PowerPoolStorage pool = PowerPoolStorage.bind(Arrays.asList(a, b));
        assertTrue(pool.canUseEnergy(10));
        assertTrue(pool.useEnergy(10));
        assertFalse(pool.useEnergy(1));
        assertEquals(2 * CAP - 5, pool.addEnergy(2 * CAP - 5));
        assertEquals(5, pool.addEnergy(10)); // only 5 more fit
        assertEquals(2 * CAP, pool.getStored());
    }

    @Test
    void leavingTakesTheShareAlong() {
        Junction a = junction(0);
        Junction b = junction(0);
        PowerPoolStorage pool = PowerPoolStorage.bind(Arrays.asList(a, b));
        pool.addEnergy(5000);
        assertEquals(2500, pool.shareOf(b));
        assertEquals(2500, pool.detach(b));
        assertEquals(2500, pool.getStored());
        assertEquals(CAP, pool.getCapacity());
        // the last member takes the rest, rounding included
        assertEquals(2500, pool.detach(a));
        assertEquals(0, pool.getStored());
    }
}
