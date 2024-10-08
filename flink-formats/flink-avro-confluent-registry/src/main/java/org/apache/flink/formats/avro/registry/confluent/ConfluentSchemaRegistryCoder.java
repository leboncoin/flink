/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.formats.avro.registry.confluent;

import org.apache.flink.formats.avro.SchemaCoder;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;

import javax.annotation.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import static java.lang.String.format;

/** Reads and Writes schema using Confluent Schema Registry protocol. */
public class ConfluentSchemaRegistryCoder implements SchemaCoder {

    private final SchemaRegistryClient schemaRegistryClient;
    private final Map<String, ?> registryConfigs;
    private String subject;
    private static final int CONFLUENT_MAGIC_BYTE = 0;

    /**
     * Creates {@link SchemaCoder} that uses provided {@link SchemaRegistryClient} to connect to
     * schema registry.
     *
     * @param schemaRegistryClient client to connect schema registry
     * @param subject subject of schema registry to produce
     */
    public ConfluentSchemaRegistryCoder(
            String subject,
            SchemaRegistryClient schemaRegistryClient,
            @Nullable Map<String, ?> registryConfigs) {
        this.schemaRegistryClient = schemaRegistryClient;
        this.subject = subject;
        this.registryConfigs = registryConfigs;
    }

    /**
     * Creates {@link SchemaCoder} that uses provided {@link SchemaRegistryClient} to connect to
     * schema registry.
     *
     * @param schemaRegistryClient client to connect schema registry
     * @param subject subject of schema registry to produce
     */
    public ConfluentSchemaRegistryCoder(String subject, SchemaRegistryClient schemaRegistryClient) {
        this(subject, schemaRegistryClient, null);
    }

    /**
     * Creates {@link SchemaCoder} that uses provided {@link SchemaRegistryClient} to connect to
     * schema registry.
     *
     * @param schemaRegistryClient client to connect schema registry
     */
    public ConfluentSchemaRegistryCoder(SchemaRegistryClient schemaRegistryClient) {
        this(null, schemaRegistryClient, null);
    }

    @Override
    public Schema readSchema(InputStream in) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(in);

        if (dataInputStream.readByte() != 0) {
            throw new IOException("Unknown data format. Magic number does not match");
        } else {
            int schemaId = dataInputStream.readInt();

            try {
                return schemaRegistryClient.getById(schemaId);
            } catch (RestClientException e) {
                throw new IOException(
                        format("Could not find schema with id %s in registry", schemaId), e);
            }
        }
    }

    @Override
    public void writeSchema(Schema schema, OutputStream out) throws IOException {
        int registeredId;
        if (registerSchema()) {
            try {
                registeredId = schemaRegistryClient.register(subject, schema);
            } catch (RestClientException e) {
                throw new IOException("Could not register schema in registry", e);
            }
        } else {
            try {
                registeredId = schemaRegistryClient.getId(subject, schema);
            } catch (RestClientException e) {
                throw new IOException("Could not retrieve schema in registry", e);
            }
        }
        out.write(CONFLUENT_MAGIC_BYTE);
        byte[] schemaIdBytes = ByteBuffer.allocate(4).putInt(registeredId).array();
        out.write(schemaIdBytes);
    }

    private boolean registerSchema() {
        return (this.registryConfigs != null
                        && registryConfigs.containsKey("auto.register.schemas"))
                ? Boolean.parseBoolean((String) registryConfigs.get("auto.register.schemas"))
                : true;
    }
}
