package com.openchat.secureim.storage;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.Transaction;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import com.openchat.secureim.entities.PreKey;
import com.openchat.secureim.entities.UnstructuredPreKeyList;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

public abstract class Keys {

  @SqlUpdate("DELETE FROM keys WHERE number = :number AND device_id = :device_id")
  abstract void removeKeys(@Bind("number") String number, @Bind("device_id") long deviceId);

  @SqlUpdate("DELETE FROM keys WHERE id = :id")
  abstract void removeKey(@Bind("id") long id);

  @SqlBatch("INSERT INTO keys (number, device_id, key_id, public_key, identity_key, last_resort) VALUES " +
      "(:number, :device_id, :key_id, :public_key, :identity_key, :last_resort)")
  abstract void append(@PreKeyBinder List<PreKey> preKeys);

  @SqlUpdate("INSERT INTO keys (number, device_id, key_id, public_key, identity_key, last_resort) VALUES " +
      "(:number, :device_id, :key_id, :public_key, :identity_key, :last_resort)")
  abstract void append(@PreKeyBinder PreKey preKey);

  @SqlQuery("SELECT * FROM keys WHERE number = :number AND device_id = :device_id ORDER BY key_id ASC FOR UPDATE")
  @Mapper(PreKeyMapper.class)
  abstract PreKey retrieveFirst(@Bind("number") String number, @Bind("device_id") long deviceId);

  @Transaction(TransactionIsolationLevel.SERIALIZABLE)
  public void store(String number, long deviceId, PreKey lastResortKey, List<PreKey> keys) {
    for (PreKey key : keys) {
      key.setNumber(number);
      key.setDeviceId(deviceId);
    }

    lastResortKey.setNumber(number);
    lastResortKey.setDeviceId(deviceId);
    lastResortKey.setLastResort(true);

    removeKeys(number, deviceId);
    append(keys);
    append(lastResortKey);
  }

  @Transaction(TransactionIsolationLevel.SERIALIZABLE)
  public UnstructuredPreKeyList get(String number, Account account) {
    List<PreKey> preKeys = new LinkedList<>();
    for (Device device : account.getDevices()) {
      PreKey preKey = retrieveFirst(number, device.getDeviceId());
      if (preKey != null)
        preKeys.add(preKey);
    }

    for (PreKey preKey : preKeys) {
      if (!preKey.isLastResort())
        removeKey(preKey.getId());
    }

    return new UnstructuredPreKeyList(preKeys);
  }

  @BindingAnnotation(PreKeyBinder.PreKeyBinderFactory.class)
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.PARAMETER})
  public @interface PreKeyBinder {
    public static class PreKeyBinderFactory implements BinderFactory {
      @Override
      public Binder build(Annotation annotation) {
        return new Binder<PreKeyBinder, PreKey>() {
          @Override
          public void bind(SQLStatement<?> sql, PreKeyBinder accountBinder, PreKey preKey)
          {
            sql.bind("id", preKey.getId());
            sql.bind("number", preKey.getNumber());
            sql.bind("device_id", preKey.getDeviceId());
            sql.bind("key_id", preKey.getKeyId());
            sql.bind("public_key", preKey.getPublicKey());
            sql.bind("identity_key", preKey.getIdentityKey());
            sql.bind("last_resort", preKey.isLastResort() ? 1 : 0);
          }
        };
      }
    }
  }


  public static class PreKeyMapper implements ResultSetMapper<PreKey> {
    @Override
    public PreKey map(int i, ResultSet resultSet, StatementContext statementContext)
        throws SQLException
    {
      return new PreKey(resultSet.getLong("id"), resultSet.getString("number"), resultSet.getLong("device_id"),
                         resultSet.getLong("key_id"), resultSet.getString("public_key"),
                         resultSet.getString("identity_key"),
                         resultSet.getInt("last_resort") == 1);
    }
  }

}
