/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.model;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Abstract method that all PASS model entities inherit from. All entities can include
 * a unique ID, type, and context
 *
 * @author Karen Hanson
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public abstract class PassEntity {

    /**
     * Unique URI for the resource. This corresponds to the URI of this resource in the
     * repository. This URI can be used to retrieve the resource from the repository
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("@id")
    protected URI id;

    /**
     * Version tag can be a version number or string of characters used to identify the object version
     * This could, for example, be an HTTP ETag. Its main purpose is for comparison during updates to
     * ensure you do not overwrite someone else's changes. Should not be part of the JSON output
     */
    @JsonIgnore
    protected String versionTag;

    /**
     * Optional context field, when present this can be used to convert the JSON to JSON-LD
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("@context")
    protected String context = null;

    /**
     * PassEntity constructor
     */
    protected PassEntity() {
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param passEntity the PassEntity to copy
     */
    protected PassEntity(PassEntity passEntity) {
        if (passEntity == null) {
            throw new IllegalArgumentException("Null object provided. When creating a copy of "
                                               + "an object, the model object cannot be null");
        }
        this.id = passEntity.id;
        this.versionTag = passEntity.versionTag;
        this.context = passEntity.context;
    }

    /**
     * Retrieves the unique URI representing the resource.
     *
     * @return the id
     */
    public URI getId() {
        return id;
    }

    /**
     * Sets the unique ID for an object. Note that when creating a new resource, this should be left
     * blank as the ID will be autogenerated and populated by the repository. When performing a
     * PUT, this URI will be used as the target resource.
     *
     * @param id the id to set
     */
    public void setId(URI id) {
        this.id = id;
    }

    /**
     * @return the context
     */
    public String getContext() {
        return context;
    }

    /**
     * @param context the context to set
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * @return the versionTag
     */
    public String getVersionTag() {
        return versionTag;
    }

    /**
     * @param versionTag the versionTag to set
     */
    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PassEntity that = (PassEntity) o;

        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (context != null ? !context.equals(that.context) : that.context != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (context != null ? context.hashCode() : 0);
        return result;
    }
}
