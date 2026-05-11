/*
 */
package vn.asg.converter.reverter.entity;

import java.util.ArrayList;
import java.util.List;
import vn.asg.converter.utils.Builder;

/**
 *
 * @author ThanhNk
 */
public abstract class BulletinBase<TEntity> {

    private List<TEntity> entities = new ArrayList<>();
    private String identify;

    //<editor-fold defaultstate="collapsed" desc="Class Property">
    
    
    /**
     * @return the entities
     */
    public List<TEntity> getEntities() {
        return entities;
    }

    /**
     * @param entities the entities to set
     */
    public void setEntities(List<TEntity> entities) {
        this.entities = entities;
    }

    /**
     * @return the identify
     */
    public String getIdentify() {
        return identify;
    }

    /**
     * @param identify the identify to set
     */
    public void setIdentify(String identify) {
        this.identify = identify;
    }
    
    //</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="Override HashCode">
    
    @Override
    public int hashCode() {
        return this.identify.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof METAR)) {
            return false;
        }
        METARBulletin other = (METARBulletin) object;

        return this.getIdentify().equalsIgnoreCase(other.getIdentify());
    }

    //</editor-fold>
    
    @Override
    public String toString() {
        Builder builder = new Builder("");
        builder.append(this.identify);
        for (TEntity metar : getEntities()) {
            builder.append("\n");
            builder.append(metar.toString());
        }
        return builder.toString();
    }

    
}


